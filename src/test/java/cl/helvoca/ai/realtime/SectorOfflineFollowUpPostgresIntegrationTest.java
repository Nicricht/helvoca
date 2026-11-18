package cl.helvoca.ai.realtime;

import cl.helvoca.onboarding.SectorResourceReadiness;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONArray;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.jobs.enabled=false"
})
class SectorOfflineFollowUpPostgresIntegrationTest {

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
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired JdbcTemplate runtimeJdbc;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;

    @Test
    void hospitalityT05PersistsFollowUpWithoutClaimingAnUnverifiedRoomBooking() throws Exception {
        JSONObject scenario = scenario("hospitality-T05");
        assertEquals("hospitality", scenario.getString("sector"));
        assertEquals("RESOURCE_UNVERIFIED", scenario.getString("sourceState"));
        assertEquals("FOLLOW_UP_OPENED", scenario.getString("expectedOutcome"));
        assertEquals("request_id_persisted", scenario.getString("requiredEvidence"));
        assertFalse(scenario.getBoolean("realExternalEffects"));
        assertEquals("DISARMED", scenario.getJSONObject("fixture").getString("providerMode"));
        assertEquals("EXPECTED_ONLY_NOT_OBSERVED", scenario.getJSONObject("fixture").getString("verification"));
        assertEquals("NOT_CERTIFIED",
                SectorResourceReadiness.forPreset("hospitality").verificationState());

        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        UUID hotel = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        String caller = "+56911111111";
        String destination = "+56222222222";
        String streamSid = "MZ-hotel-followup-" + UUID.randomUUID();

        owner.update("INSERT INTO business(id, name, timezone, language) VALUES (?, 'Hotel offline', 'America/Santiago', 'es')", hotel);
        owner.update("INSERT INTO business(id, name, timezone, language) VALUES (?, 'Otra clínica', 'America/Santiago', 'es')", otherTenant);
        owner.update("INSERT INTO business_profile(business_id, preset_key) VALUES (?, 'hospitality')", hotel);
        owner.update("INSERT INTO business_profile(business_id, preset_key) VALUES (?, 'clinic')", otherTenant);
        owner.update("INSERT INTO phone_number(id, business_id, provider, phone_number, active) VALUES (?, ?, 'TWILIO', ?, TRUE)",
                phoneId, hotel, destination);
        owner.update("""
                INSERT INTO call_session(
                    id, business_id, phone_number_id, provider_call_id,
                    caller_number, destination_number, direction, status, started_at,
                    stream_sid, telephony_provider, certification)
                VALUES (?, ?, ?, ?, ?, ?, 'INBOUND', 'IN_PROGRESS', NOW(), ?, 'twilio', TRUE)
                """, callId, hotel, phoneId, "CA-offline-" + callId, caller, destination, streamSid);

        RealtimeCallContext verifiedCall = new RealtimeCallContext(
                callId, hotel, null, caller, destination, streamSid);
        JSONObject args = new JSONObject()
                .put("requestType", "ROOM_AVAILABILITY")
                .put("title", "Verificar habitación doble por tres noches")
                .put("description", "La disponibilidad por habitación y noche no está verificada; requiere revisión humana.")
                .put("priority", "NORMAL")
                .put("detailsJson", new JSONObject()
                        .put("resourceKind", "ROOM_NIGHT")
                        .put("availabilityVerified", false).toString());

        JSONObject response = new JSONObject(tools.execute(
                verifiedCall, "create_request", args.toString()));
        assertTrue(response.getBoolean("success"), response.toString());
        JSONObject data = response.getJSONObject("data");
        UUID requestId = UUID.fromString(data.getString("requestId"));
        UUID operationId = UUID.fromString(data.getString("operationId"));
        assertEquals("OPEN", data.getString("status"));
        assertEquals("ROOM_AVAILABILITY", data.getString("requestType"));

        assertEquals(1, owner.queryForObject("""
                SELECT count(*) FROM business_request
                WHERE id = ? AND business_id = ? AND call_id = ? AND operation_id = ?
                    AND status = 'OPEN'
                """, Integer.class, requestId, hotel, callId, operationId));
        assertEquals(1, owner.queryForObject("""
                SELECT count(*) FROM business_operation
                WHERE id = ? AND business_id = ? AND type = 'REQUEST'
                """, Integer.class, operationId, hotel));
        assertEquals(0, owner.queryForObject(
                "SELECT count(*) FROM booking WHERE business_id IN (?, ?)",
                Integer.class, hotel, otherTenant));

        // Both the follow-up and its linked operation must remain tenant-isolated.
        assertEquals(1, databaseContext.callAsTenant(hotel,
                () -> runtimeJdbc.queryForObject(
                        "SELECT count(*) FROM business_operation WHERE id = ?",
                        Integer.class, operationId)));
        assertEquals(0, databaseContext.callAsTenant(otherTenant,
                () -> runtimeJdbc.queryForObject(
                        "SELECT count(*) FROM business_operation WHERE id = ?",
                        Integer.class, operationId)));

        // The owning tenant must also be able to read the durable request through runtime RLS.
        assertEquals(1, databaseContext.callAsTenant(hotel,
                () -> runtimeJdbc.queryForObject(
                        "SELECT count(*) FROM business_request WHERE id = ?",
                        Integer.class, requestId)));

        // The actual runtime datasource must enforce PostgreSQL tenant RLS.
        assertEquals(0, databaseContext.callAsTenant(otherTenant,
                () -> runtimeJdbc.queryForObject(
                        "SELECT count(*) FROM business_request WHERE id = ?",
                        Integer.class, requestId)));

        // A foreign tenant must not be able to mutate the owner's durable follow-up.
        assertEquals(0, databaseContext.callAsTenant(otherTenant,
                () -> runtimeJdbc.update(
                        "UPDATE business_request SET status = 'CLOSED' WHERE id = ?", requestId)));
        assertEquals("OPEN", owner.queryForObject(
                "SELECT status FROM business_request WHERE id = ? AND business_id = ?",
                String.class, requestId, hotel));

        RealtimeCallContext foreignTenant = new RealtimeCallContext(
                callId, otherTenant, null, caller, destination, streamSid);
        JSONObject foreignResult = new JSONObject(tools.execute(
                foreignTenant, "create_request", args.toString()));
        assertFalse(foreignResult.getBoolean("success"), foreignResult.toString());

        RealtimeCallContext wrongStream = new RealtimeCallContext(
                callId, hotel, null, caller, destination, "MZ-untrusted");
        JSONObject wrongStreamResult = new JSONObject(tools.execute(
                wrongStream, "create_request", args.toString()));
        assertFalse(wrongStreamResult.getBoolean("success"), wrongStreamResult.toString());

        assertEquals(1, owner.queryForObject(
                "SELECT count(*) FROM business_request WHERE business_id = ?",
                Integer.class, hotel));
        assertEquals(0, owner.queryForObject(
                "SELECT count(*) FROM business_request WHERE business_id = ?",
                Integer.class, otherTenant));
        assertEquals(0, owner.queryForObject(
                "SELECT count(*) FROM booking WHERE business_id IN (?, ?)",
                Integer.class, hotel, otherTenant));
    }

    private static JSONObject scenario(String id) throws Exception {
        try (InputStream stream = SectorOfflineFollowUpPostgresIntegrationTest.class
                .getResourceAsStream("/fixtures/sector-offline-180-v1.json")) {
            assertNotNull(stream, "versioned F1 scenario catalog must exist");
            JSONObject root = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("FIXTURE_ONLY_NOT_EXECUTED", root.getString("certificationState"));
            JSONArray scenarios = root.getJSONArray("scenarios");
            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject candidate = scenarios.getJSONObject(i);
                if (id.equals(candidate.getString("id"))) return candidate;
            }
            throw new AssertionError("Missing scenario " + id);
        }
    }
}
