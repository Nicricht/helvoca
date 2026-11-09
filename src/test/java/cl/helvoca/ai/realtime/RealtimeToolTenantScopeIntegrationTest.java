package cl.helvoca.ai.realtime;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.jobs.enabled=false"
})
class RealtimeToolTenantScopeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired RealtimeToolService tools;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;

    @Test
    void agentGreetingReadsOnlyTheBusinessFromRealtimeContextWithoutAmbientSystemScope() {
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        UUID businessId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        owner.update("""
                INSERT INTO business(id, name, timezone, language)
                VALUES (?, 'Realtime Tenant', 'America/Santiago', 'es')
                """, businessId);
        owner.update("""
                INSERT INTO ai_agent(id, business_id, name, language, greeting, active)
                VALUES (?, ?, 'RecepVoz', 'es', 'Saludo aislado por tenant', TRUE)
                """, agentId, businessId);

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(),
                businessId,
                null,
                "+56911111111",
                "+56222222222",
                "MZ-tenant-scope");

        assertEquals(
                "Saludo aislado por tenant",
                tools.agentGreeting(context, "fallback"));
    }

    @Test
    void callerNameCorrectionPersistsOnSameCustomerAndPreservesBookingAndTenantIsolation() {
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        UUID businessId = UUID.randomUUID();
        UUID otherBusinessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String callerNumber = "+56911111111";
        String streamSid = "MZ-postgres-name-correction";

        owner.update("INSERT INTO business(id, name, timezone, language) VALUES (?, 'Aurora Test', 'America/Santiago', 'es')",
                businessId);
        owner.update("INSERT INTO business(id, name, timezone, language) VALUES (?, 'Other Tenant', 'America/Santiago', 'es')",
                otherBusinessId);
        owner.update("INSERT INTO customer(id, business_id, name, phone) VALUES (?, ?, 'Nombre mal reconocido', ?)",
                customerId, businessId, callerNumber);
        owner.update("INSERT INTO customer(id, business_id, name, phone) VALUES (?, ?, 'No modificar', ?)",
                otherCustomerId, otherBusinessId, callerNumber);
        owner.update("INSERT INTO service(id, business_id, name, duration_minutes, active) VALUES (?, ?, 'Consulta', 30, TRUE)",
                serviceId, businessId);
        owner.update("""
                INSERT INTO booking(id, business_id, customer_id, service_id, start_at, end_at, status, source)
                VALUES (?, ?, ?, ?, NOW() + INTERVAL '2 days', NOW() + INTERVAL '2 days 30 minutes', 'CONFIRMED', 'AI_CALL')
                """, bookingId, businessId, customerId, serviceId);
        owner.update("""
                INSERT INTO phone_number(id, business_id, provider, phone_number, active)
                VALUES (?, ?, 'TWILIO', '+56222222222', TRUE)
                """, phoneId, businessId);
        owner.update("""
                INSERT INTO call_session(
                    id, business_id, customer_id, phone_number_id, provider_call_id,
                    caller_number, destination_number, direction, status, started_at,
                    stream_sid, telephony_provider, certification)
                VALUES (?, ?, ?, ?, 'CA-postgres-name-correction', ?, '+56222222222',
                        'OUTBOUND', 'IN_PROGRESS', NOW(), ?, 'twilio', TRUE)
                """, callId, businessId, customerId, phoneId, callerNumber, streamSid);

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, callerNumber, "+56222222222", streamSid);
        JSONObject result = new JSONObject(tools.execute(
                context,
                "register_caller",
                new JSONObject().put("name", "Nombre corregido").toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(customerId.toString(), result.getJSONObject("data").getString("customerId"));
        assertEquals("Nombre corregido", owner.queryForObject(
                "SELECT name FROM customer WHERE id = ? AND business_id = ?", String.class,
                customerId, businessId));
        assertEquals(1, owner.queryForObject(
                "SELECT count(*) FROM customer WHERE business_id = ? AND phone = ?", Integer.class,
                businessId, callerNumber));
        assertEquals(customerId, owner.queryForObject(
                "SELECT customer_id FROM call_session WHERE id = ?", UUID.class, callId));
        assertEquals(customerId, owner.queryForObject(
                "SELECT customer_id FROM booking WHERE id = ?", UUID.class, bookingId));
        assertEquals("No modificar", owner.queryForObject(
                "SELECT name FROM customer WHERE id = ? AND business_id = ?", String.class,
                otherCustomerId, otherBusinessId));
    }
}
