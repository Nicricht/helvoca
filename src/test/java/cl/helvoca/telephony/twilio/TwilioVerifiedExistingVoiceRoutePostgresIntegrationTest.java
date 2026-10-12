package cl.helvoca.telephony.twilio;

import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Simulated platform audit in disposable PostgreSQL 16, no Twilio API or production. */
@Testcontainers
@SpringBootTest(properties = "HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=true")
class TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID TENANT = UUID.fromString("9421dc18-c2de-440d-8730-bd5aa5c22301");
    private static final UUID PHONE_ID = UUID.fromString("9df0f471-4253-482e-ad77-1aec21eb0767");
    private static final UUID REQUESTER = UUID.fromString("772fb020-ece4-4907-89cd-5c4f66e7c01d");
    private static final UUID APPROVER = UUID.fromString("34395ec8-b431-4666-9b35-187e8305da0e");
    private static final String E164 = "+12025550305";
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String PN = "PN" + "b".repeat(32);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.seed.enabled", () -> "false");
        r.add("app.telephony.twilio.existing-voice-route-enabled", () -> "true");
        r.add("app.telephony.twilio.existing-voice-route-business-id", TENANT::toString);
        r.add("app.telephony.twilio.existing-voice-route-phone-id", PHONE_ID::toString);
        r.add("app.telephony.twilio.existing-voice-route-account-sid", () -> ACCOUNT);
    }

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantDatabaseContext scope;
    @Autowired PhoneNumberRepository phones;
    @Autowired TwilioVerifiedExistingVoiceRouteGate gate;
    @Autowired TwilioCallBindingGuard binding;

    private JdbcTemplate owner;

    @BeforeEach
    void resetFakeInventory() {
        owner = new JdbcTemplate(migrationDataSource);
        // V102/V103 audit tables are SYSTEM-only append-only. A disposable
        // Testcontainers migration-owner connection resets fixtures; never
        // bypass these privileges in the application or in production.
        owner.update("DELETE FROM twilio_existing_enrollment_audit");
        owner.update("DELETE FROM twilio_existing_enrollment_claim");
        owner.update("DELETE FROM twilio_existing_enrollment_request");
        scope.callAsSystem(() -> jdbc.update("DELETE FROM phone_number WHERE id = ?", PHONE_ID));
        owner.update("DELETE FROM pilot_launch_control WHERE business_id = ?", TENANT);
        owner.update("INSERT INTO business(id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                TENANT, "Synthetic phase4 tenant");
        for (UUID admin : new UUID[]{REQUESTER, APPROVER}) {
            owner.update("""
                    INSERT INTO app_user(id,business_id,name,email,password_hash,active)
                    VALUES (?,NULL,?,?,'test-hash',TRUE) ON CONFLICT DO NOTHING
                    """, admin, "Synthetic admin", "test-" + admin + "@example.test");
        }
        owner.update("""
                INSERT INTO phone_number(id,business_id,phone_number,provider,external_id,active)
                VALUES (?,? ,?,'TWILIO',?,TRUE)
                """, PHONE_ID, TENANT, E164, PN);
        scope.callAsSystem(() -> jdbc.update("""
                UPDATE phone_number SET twilio_binding_source = 'VERIFIED_EXISTING',
                    twilio_account_sid = ?, voice_verified_at = ?
                WHERE id = ?
                """, ACCOUNT, Timestamp.from(Instant.now()), PHONE_ID));
        owner.update("INSERT INTO pilot_launch_control(business_id,status) VALUES (?, 'RUNNING')", TENANT);
    }

    private boolean allowed() {
        return scope.callAsSystem(() -> gate.allows(phones.findById(PHONE_ID).orElseThrow()));
    }

    @Test
    void mereVerifiedSourceAndRunningPilotCannotRouteBeforeTwoPersonAuditCompletes() {
        assertFalse(allowed());
        assertFalse(binding.allowsIncoming("CA" + "c".repeat(32), E164, "+56911111111", ACCOUNT));
        UUID change = UUID.randomUUID();
        scope.callAsSystem(() -> {
            jdbc.update("""
                    INSERT INTO twilio_existing_enrollment_request (
                        change_id,business_id,phone_number_id,destination_e164,owner_account_sid,
                        requester_id,requested_at,expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10 minutes')
                    """, change, TENANT, PHONE_ID, E164, ACCOUNT, REQUESTER);
            jdbc.update("""
                    UPDATE twilio_existing_enrollment_request SET state = 'APPROVED',
                        approver_id = ?, approved_at = clock_timestamp() WHERE change_id = ?
                    """, APPROVER, change);
            jdbc.update("""
                    UPDATE twilio_existing_enrollment_request SET state = 'APPLIED',
                        executed_at = clock_timestamp() WHERE change_id = ?
                    """, change);
            jdbc.update("""
                    INSERT INTO twilio_existing_enrollment_claim (
                        change_id,business_id,phone_number_id,destination_e164,
                        owner_account_sid,phone_sid,requester_id,approver_id,enrolled_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, change, TENANT, PHONE_ID, E164, ACCOUNT, PN, REQUESTER, APPROVER);
            return null;
        });
        // A request and claim without EXECUTED audit cannot activate voice.
        assertFalse(allowed());
        scope.callAsSystem(() -> jdbc.update("""
                INSERT INTO twilio_existing_enrollment_audit(
                    id,change_id,actor_id,action,outcome
                ) VALUES (?, ?, ?, 'EXECUTED', 'APPLIED')
                """, UUID.randomUUID(), change, APPROVER));

        assertTrue(allowed());
        assertTrue(binding.allowsIncoming("CA" + "c".repeat(32), E164, "+56911111111", ACCOUNT));
        assertFalse(binding.allowsIncoming("CA" + "c".repeat(32), E164, "+56911111111",
                "AC" + "d".repeat(32)));
        assertFalse(binding.allowsIncoming("CA" + "c".repeat(32), "+12025559999",
                "+56911111111", ACCOUNT));

        owner.update("UPDATE pilot_launch_control SET status = 'PAUSED' WHERE business_id = ?", TENANT);
        assertFalse(allowed());
        assertFalse(binding.allowsIncoming("CA" + "c".repeat(32), E164, "+56911111111", ACCOUNT));
    }

    @Test
    void unregisteredPilotIsNeverAllowedEvenWhenGlobalSwitchIsEnabled() {
        owner.update("DELETE FROM pilot_launch_control WHERE business_id = ?", TENANT);
        assertFalse(allowed());
    }
}
