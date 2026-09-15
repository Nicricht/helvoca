package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class SafeOperationRetryEngineIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired SafeOperationRetryEngine retries;
    @Autowired BusinessRepository businesses;
    @Autowired JdbcTemplate jdbc;

    @Test
    void retriesTransientFailuresAndCommitsOnlySuccessfulAttempt() {
        Business business = business("Retry Success");
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        String raw = retries.execute(
                business.getId(),
                BusinessOperation.Type.QUOTE,
                sourceReferenceId,
                "create_quote",
                () -> {
                    int call = calls.incrementAndGet();

                    // The same PK is inserted on every attempt. Attempts one and two
                    // return a transient failure and must roll this insert back. The
                    // third attempt can only insert and commit successfully if those
                    // earlier side effects were truly reverted.
                    jdbc.update("""
                            INSERT INTO business_automation_policy(
                                business_id, operation_type, auto_execute, customer_confirmation,
                                payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                            VALUES (?, 'QUOTE', TRUE, 'NONE', 'NONE', 'SAFE_AUTOMATIC', 2,
                                    'ONLY_IF_UNRESOLVABLE')
                            """, business.getId());

                    if (call < 3) return failure("COMMERCIAL_OPERATION_FAILED").toString();
                    return success(new JSONObject().put("operationId", operationId.toString())).toString();
                });

        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals(3, calls.get());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM business_automation_policy
                WHERE business_id = ? AND operation_type = 'QUOTE'
                """, Integer.class, business.getId()));

        List<Map<String, Object>> attempts = jdbc.queryForList("""
                SELECT attempt_no, max_attempts, outcome, failure_class, error_code, delay_ms
                FROM business_operation_retry_attempt
                WHERE business_id = ?
                ORDER BY sequence_no
                """, business.getId());

        assertEquals(3, attempts.size());
        assertEquals("RETRY_SCHEDULED", attempts.get(0).get("outcome"));
        assertEquals(100, ((Number) attempts.get(0).get("delay_ms")).intValue());
        assertEquals("RETRY_SCHEDULED", attempts.get(1).get("outcome"));
        assertEquals(200, ((Number) attempts.get(1).get("delay_ms")).intValue());
        assertEquals("SUCCEEDED_AFTER_RETRY", attempts.get(2).get("outcome"));
        assertEquals(3, ((Number) attempts.get(2).get("attempt_no")).intValue());
        assertEquals(3, ((Number) attempts.get(2).get("max_attempts")).intValue());
    }

    @Test
    void appliesFallbackWithoutRetryingBusinessFailure() {
        Business business = business("Fallback");
        AtomicInteger calls = new AtomicInteger();

        String raw = retries.execute(
                business.getId(),
                BusinessOperation.Type.BOOKING,
                UUID.randomUUID(),
                "create_booking",
                () -> {
                    calls.incrementAndGet();
                    return failure("BOOKING_SLOT_UNAVAILABLE").toString();
                });

        JSONObject result = new JSONObject(raw);
        assertFalse(result.getBoolean("success"));
        assertEquals(1, calls.get());
        JSONObject automation = result.getJSONObject("automation");
        assertEquals("RESOLVABLE_WITH_FALLBACK", automation.getString("failureClass"));
        assertEquals("REFRESH_AVAILABILITY", automation.getString("fallbackAction"));
        assertFalse(automation.getBoolean("humanEscalation"));
        assertEquals(0, automation.getInt("retryCount"));

        assertEquals("FALLBACK_APPLIED", jdbc.queryForObject("""
                SELECT outcome FROM business_operation_retry_attempt
                WHERE business_id = ? ORDER BY sequence_no DESC LIMIT 1
                """, String.class, business.getId()));
    }

    @Test
    void retryNoneExecutesOnceAndReportsExhaustedTransientFailure() {
        Business business = business("Retry Disabled");
        jdbc.update("""
                INSERT INTO business_automation_policy(
                    business_id, operation_type, auto_execute, customer_confirmation,
                    payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                VALUES (?, 'LEAD', TRUE, 'NONE', 'NONE', 'NONE', 0, 'ONLY_IF_UNRESOLVABLE')
                """, business.getId());

        AtomicInteger calls = new AtomicInteger();
        String raw = retries.execute(
                business.getId(),
                BusinessOperation.Type.LEAD,
                UUID.randomUUID(),
                "create_lead",
                () -> {
                    calls.incrementAndGet();
                    return failure("COMMERCIAL_OPERATION_FAILED").toString();
                });

        JSONObject result = new JSONObject(raw);
        assertEquals(1, calls.get());
        assertFalse(result.getBoolean("success"));
        assertEquals(0, result.getJSONObject("automation").getInt("retryCount"));
        assertEquals("RETRIES_EXHAUSTED", jdbc.queryForObject("""
                SELECT outcome FROM business_operation_retry_attempt
                WHERE business_id = ? ORDER BY sequence_no DESC LIMIT 1
                """, String.class, business.getId()));
    }

    @Test
    void retryHistoryIsAppendOnly() {
        Business business = business("Append Only");
        retries.execute(
                business.getId(),
                BusinessOperation.Type.REQUEST,
                UUID.randomUUID(),
                "create_request",
                () -> failure("INVALID_ARGUMENT").toString());

        UUID id = jdbc.queryForObject("""
                SELECT id FROM business_operation_retry_attempt
                WHERE business_id = ? ORDER BY sequence_no DESC LIMIT 1
                """, UUID.class, business.getId());
        assertNotNull(id);
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE business_operation_retry_attempt SET delay_ms = 1 WHERE id = ?", id));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM business_operation_retry_attempt WHERE id = ?", id));
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private static JSONObject failure(String code) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", "test"));
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL);
    }
}
