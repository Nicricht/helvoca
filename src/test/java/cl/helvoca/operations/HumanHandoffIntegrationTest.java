package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class HumanHandoffIntegrationTest {

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
    @Autowired HumanHandoffService handoffs;
    @Autowired BusinessRepository businesses;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unresolvableEscalationCreatesOneDurableHandoffAndDeduplicates() {
        Business business = business("Handoff Dedup");
        UUID sourceReferenceId = UUID.randomUUID();

        String firstRaw = retries.execute(
                business.getId(),
                BusinessOperation.Type.PAYMENT,
                sourceReferenceId,
                "create_payment",
                () -> failure("PAYMENT_PROVIDER_NOT_CONFIGURED").toString());

        JSONObject first = new JSONObject(firstRaw).getJSONObject("automation");
        assertEquals("UNRESOLVABLE", first.getString("failureClass"));
        assertEquals("HUMAN_HANDOFF", first.getString("fallbackAction"));
        assertTrue(first.getBoolean("humanEscalation"));
        assertTrue(first.getBoolean("handoffRequested"));
        assertTrue(first.getBoolean("handoffCreated"));
        UUID handoffId = UUID.fromString(first.getString("handoffId"));

        String secondRaw = retries.execute(
                business.getId(),
                BusinessOperation.Type.PAYMENT,
                sourceReferenceId,
                "create_payment",
                () -> failure("PAYMENT_PROVIDER_NOT_CONFIGURED").toString());

        JSONObject second = new JSONObject(secondRaw).getJSONObject("automation");
        assertTrue(second.getBoolean("humanEscalation"));
        assertFalse(second.getBoolean("handoffCreated"));
        assertEquals(handoffId.toString(), second.getString("handoffId"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_handoff WHERE business_id = ?",
                Integer.class, business.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_handoff_event WHERE handoff_id = ? AND event_type = 'CREATED'",
                Integer.class, handoffId));
    }

    @Test
    void resolvableFailureNeverCreatesHandoff() {
        Business business = business("No Handoff For Fallback");

        String raw = retries.execute(
                business.getId(),
                BusinessOperation.Type.BOOKING,
                UUID.randomUUID(),
                "create_booking",
                () -> failure("BOOKING_SLOT_UNAVAILABLE").toString());

        JSONObject automation = new JSONObject(raw).getJSONObject("automation");
        assertEquals("RESOLVABLE_WITH_FALLBACK", automation.getString("failureClass"));
        assertFalse(automation.getBoolean("humanEscalation"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_handoff WHERE business_id = ?",
                Integer.class, business.getId()));
    }

    @Test
    void escalationPolicyNeverStopsSafelyWithoutCreatingHandoff() {
        Business business = business("Escalation Disabled");
        jdbc.update("""
                INSERT INTO business_automation_policy(
                    business_id, operation_type, auto_execute, customer_confirmation,
                    payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                VALUES (?, 'REQUEST', TRUE, 'NONE', 'NONE', 'SAFE_AUTOMATIC', 2, 'NEVER')
                """, business.getId());

        String raw = retries.execute(
                business.getId(),
                BusinessOperation.Type.REQUEST,
                UUID.randomUUID(),
                "create_request",
                () -> failure("PAYMENT_PROVIDER_CONFIGURATION_INVALID").toString());

        JSONObject automation = new JSONObject(raw).getJSONObject("automation");
        assertEquals("UNRESOLVABLE", automation.getString("failureClass"));
        assertEquals("STOP_SAFELY", automation.getString("fallbackAction"));
        assertFalse(automation.getBoolean("humanEscalation"));
        assertFalse(automation.getBoolean("handoffRequested"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM human_handoff WHERE business_id = ?",
                Integer.class, business.getId()));
    }

    @Test
    void lifecycleIsTenantScopedAndHistoryIsAppendOnly() {
        Business businessA = business("Tenant A");
        Business businessB = business("Tenant B");
        UUID sourceReferenceId = UUID.randomUUID();

        HumanHandoffService.Creation created = handoffs.createForUnresolvable(
                businessA.getId(), BusinessOperation.Type.QUOTE, sourceReferenceId, null,
                "create_quote", "UNEXPECTED_OPERATION_FAILURE", 0);
        assertTrue(created.created());

        authenticate(businessA.getId());
        assertEquals(1, handoffs.recent("OPEN").size());
        HumanHandoffService.HandoffView assigned = handoffs.assign(
                created.handoffId(), "operador@negocio.cl", "admin@negocio.cl");
        assertEquals(HumanHandoffService.Status.ASSIGNED, assigned.status());
        assertEquals("operador@negocio.cl", assigned.assignedTo());
        HumanHandoffService.HandoffView resolved = handoffs.resolve(
                created.handoffId(), "admin@negocio.cl");
        assertEquals(HumanHandoffService.Status.RESOLVED, resolved.status());

        List<HumanHandoffService.EventView> history = handoffs.history(created.handoffId());
        assertEquals(List.of("CREATED", "ASSIGNED", "RESOLVED"),
                history.stream().map(HumanHandoffService.EventView::eventType).toList());

        UUID eventId = history.getFirst().id();
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM human_handoff_event WHERE id = ?", eventId));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE human_handoff_event SET actor_type = 'AUTOMATION' WHERE id = ?", eventId));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM human_handoff WHERE id = ?", created.handoffId()));

        authenticate(businessB.getId());
        assertTrue(handoffs.recent(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> handoffs.history(created.handoffId()));
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private static void authenticate(UUID businessId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .claim("business_id", businessId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }

    private static JSONObject failure(String code) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", "test"));
    }
}
