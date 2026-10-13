package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.twilio.TwilioCallBindingGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fresh version-selectable PostgreSQL + full Flyway migrations. No Twilio HTTP or external calls.
 * The normal runtime DataSource enforces SET ROLE and row-level security.
 */
@Testcontainers
@SpringBootTest
class TwilioVerifiedExistingMigrationPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(System.getProperty("helvoca.test.postgres.image", "postgres:16-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.seed.enabled", () -> "false");
    }

    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String ACCOUNT_B = "AC" + "b".repeat(32);
    private static final String PN = "PN" + "c".repeat(32);
    private static final String PN_B = "PN" + "d".repeat(32);
    private static final Instant VERIFIED_AT = Instant.parse("2026-10-11T15:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired JdbcTemplate runtimeJdbc;
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired PhoneNumberRepository phones;
    @Autowired TwilioCallBindingGuard incomingBinding;

    private JdbcTemplate owner;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seed() {
        owner = new JdbcTemplate(migrationDataSource);
        // Each test starts with a clean phone inventory; these are container-only rows.
        databaseContext.callAsSystem(() -> runtimeJdbc.update("DELETE FROM phone_number"));
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        owner.update("INSERT INTO business(id, name) VALUES (?, ?)",
                tenantA, "Verified Existing A " + tenantA);
        owner.update("INSERT INTO business(id, name) VALUES (?, ?)",
                tenantB, "Verified Existing B " + tenantB);
    }

    private static String number() {
        return "+1202555" + String.format(Locale.ROOT, "%04d", SEQUENCE.incrementAndGet());
    }

    private UUID insertLegacy(UUID tenant, String e164, String sid, boolean active) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO phone_number (id, business_id, phone_number, provider,
                                          external_id, active)
                VALUES (?, ?, ?, 'TWILIO', ?, ?)
                """, id, tenant, e164, sid, active);
        return id;
    }

    private int promote(UUID id, String source, String account, String sid,
                        Instant verifiedAt, String provider, String e164) {
        return runtimeJdbc.update("""
                UPDATE phone_number
                   SET twilio_binding_source = ?, twilio_account_sid = ?,
                       external_id = ?, voice_verified_at = ?,
                       provider = ?, phone_number = ?
                 WHERE id = ?
                """, source, account, sid,
                verifiedAt == null ? null : Timestamp.from(verifiedAt),
                provider, e164, id);
    }

    private int promoteSystem(UUID id, String source, String account, String sid,
                              Instant verifiedAt, String provider, String e164) {
        return databaseContext.callAsSystem(() ->
                promote(id, source, account, sid, verifiedAt, provider, e164));
    }

    private String source(UUID id) {
        return owner.queryForObject(
                "SELECT twilio_binding_source FROM phone_number WHERE id = ?",
                String.class, id);
    }

    private long count(UUID id) {
        return owner.queryForObject("SELECT COUNT(*) FROM phone_number WHERE id = ?",
                Long.class, id);
    }

    @Test
    void flywayV101AddsIndependentProvenanceWithoutChangingRlsOrLegacySchema() {
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '101' AND success
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname = 'chk_phone_twilio_binding_source'
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname = 'chk_phone_twilio_binding_account'
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'phone_number'
                  AND indexname = 'uq_phone_twilio_certified_external'
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'phone_number'
                  AND indexname = 'uq_phone_number_provider_external'
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'phone_number'
                  AND indexname = 'uq_phone_managed_twilio_account_sid'
                """, Long.class));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_trigger
                WHERE tgname = 'trg_phone_verified_existing_write_guard'
                  AND NOT tgisinternal
                """, Long.class));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT relrowsecurity AND relforcerowsecurity
                FROM pg_class WHERE oid = 'public.phone_number'::regclass
                """, Boolean.class)));
    }

    @Test
    void existingNullProvenanceLegacyRowsStayUnenrolledAndRetainTheirState() {
        String e164 = number();
        UUID noSid = insertLegacy(tenantA, e164, null, true);
        UUID oldSid = insertLegacy(tenantB, number(), PN_B, false);

        assertNull(source(noSid));
        assertNull(source(oldSid));
        assertNull(owner.queryForObject(
                "SELECT twilio_account_sid FROM phone_number WHERE id = ?",
                String.class, noSid));
        assertNull(owner.queryForObject(
                "SELECT voice_verified_at::text FROM phone_number WHERE id = ?",
                String.class, noSid));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?",
                Boolean.class, noSid)));
        assertFalse(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?",
                Boolean.class, oldSid)));
        assertEquals(PN_B, owner.queryForObject(
                "SELECT external_id FROM phone_number WHERE id = ?",
                String.class, oldSid));
        assertEquals(1L, count(noSid));
        assertEquals(1L, count(oldSid));
    }

    @Test
    void unchangedManagedPurchaseAcceptsUnverifiedVoiceAndMaintainsOriginalIdentity() {
        UUID phone = insertLegacy(tenantA, number(), PN, false);
        String e164 = owner.queryForObject(
                "SELECT phone_number FROM phone_number WHERE id = ?", String.class, phone);
        assertEquals(1, promoteSystem(phone, "MANAGED_PURCHASE",
                ACCOUNT, PN, null, "TWILIO", e164));
        assertEquals("MANAGED_PURCHASE", source(phone));
        assertNull(owner.queryForObject(
                "SELECT voice_verified_at::text FROM phone_number WHERE id = ?",
                String.class, phone));
    }

    @Test
    void verifiedExistingRequiresCompleteAtomicFieldsAndDoesNotChangeActive() {
        String e164 = number();
        UUID phone = insertLegacy(tenantA, e164, null, false);
        assertEquals(1, promoteSystem(phone, "VERIFIED_EXISTING",
                ACCOUNT, PN, VERIFIED_AT, "TWILIO", e164));
        assertEquals("VERIFIED_EXISTING", source(phone));
        assertEquals(ACCOUNT, owner.queryForObject(
                "SELECT twilio_account_sid FROM phone_number WHERE id = ?",
                String.class, phone));
        assertEquals(PN, owner.queryForObject(
                "SELECT external_id FROM phone_number WHERE id = ?",
                String.class, phone));
        assertFalse(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?",
                Boolean.class, phone)));
        // Phase 7 call binding still admits ONLY MANAGED_PURCHASE.
        assertFalse(incomingBinding.allowsIncoming("CA" + "f".repeat(32),
                e164, "+12025550999", ACCOUNT));
        assertTrue(databaseContext.callAsTenant(tenantA,
                () -> phones.findById(phone).isPresent()));
        assertFalse(databaseContext.callAsTenant(tenantB,
                () -> phones.findById(phone).isPresent()));
    }

    @Test
    void rejectsEveryIncompleteOrFalseVerifiedExistingRowWithoutChangingLegacy() {
        record Invalid(String source, String account, String sid,
                       Instant verifiedAt, String provider, String e164) {}
        String e164 = number();
        UUID id = insertLegacy(tenantA, e164, null, false);
        Invalid[] invalid = {
                new Invalid("VERIFIED_EXISTING", null, PN, VERIFIED_AT, "TWILIO", e164),
                new Invalid("VERIFIED_EXISTING", "ACinvalid", PN, VERIFIED_AT, "TWILIO", e164),
                new Invalid("VERIFIED_EXISTING", ACCOUNT, null, VERIFIED_AT, "TWILIO", e164),
                new Invalid("VERIFIED_EXISTING", ACCOUNT, "PNinvalid", VERIFIED_AT, "TWILIO", e164),
                new Invalid("VERIFIED_EXISTING", ACCOUNT, PN, null, "TWILIO", e164),
                new Invalid("VERIFIED_EXISTING", ACCOUNT, PN, VERIFIED_AT, "OTHER", e164),
                new Invalid("VERIFIED_EXISTING", ACCOUNT, PN, VERIFIED_AT, "TWILIO", "bad number"),
                new Invalid("BYON", ACCOUNT, PN, VERIFIED_AT, "TWILIO", e164),
                new Invalid("", ACCOUNT, PN, VERIFIED_AT, "TWILIO", e164)
        };
        for (Invalid sample : invalid) {
            assertThrows(DataAccessException.class, () ->
                    promoteSystem(id, sample.source(), sample.account(), sample.sid(),
                            sample.verifiedAt(), sample.provider(), sample.e164()));
            assertNull(source(id));
            assertEquals(1L, count(id));
        }
    }

    @Test
    void legacyAndManagedBindingsCannotUseAccountSidWithoutTruthfulSource() {
        String e164 = number();
        UUID id = insertLegacy(tenantA, e164, null, true);
        assertThrows(DataAccessException.class, () ->
                promoteSystem(id, null, ACCOUNT, PN, VERIFIED_AT, "TWILIO", e164));
        assertThrows(DataAccessException.class, () ->
                promoteSystem(id, "MANAGED_PURCHASE", ACCOUNT, null, VERIFIED_AT, "TWILIO", e164));
        assertThrows(DataAccessException.class, () ->
                promoteSystem(id, null, null, PN, VERIFIED_AT, "TWILIO", e164));
        assertNull(source(id));
    }

    @Test
    void oldManagedAndNewVerifiedCannotDuplicateOneCarrierPhoneSidAcrossTenants() {
        UUID managed = insertLegacy(tenantA, number(), PN, true);
        String managedNumber = owner.queryForObject(
                "SELECT phone_number FROM phone_number WHERE id = ?",
                String.class, managed);
        assertEquals(1, promoteSystem(managed, "MANAGED_PURCHASE",
                ACCOUNT, PN, VERIFIED_AT, "TWILIO", managedNumber));

        String secondNumber = number();
        UUID legacy = insertLegacy(tenantB, secondNumber, null, true);
        assertThrows(DataAccessException.class, () ->
                promoteSystem(legacy, "VERIFIED_EXISTING",
                        ACCOUNT_B, PN, VERIFIED_AT, "TWILIO", secondNumber));
        assertNull(source(legacy));
        assertEquals("MANAGED_PURCHASE", source(managed));
    }

    @Test
    void noTenantMaySelfCertifyAndOtherTenantsCannotEvenAddressTheRow() {
        String e164 = number();
        UUID id = insertLegacy(tenantA, e164, null, false);
        assertThrows(DataAccessException.class, () ->
                databaseContext.callAsTenant(tenantA, () ->
                        promote(id, "VERIFIED_EXISTING", ACCOUNT, PN,
                                VERIFIED_AT, "TWILIO", e164)));
        assertNull(source(id));

        assertEquals(0, databaseContext.callAsTenant(tenantB, () ->
                promote(id, "VERIFIED_EXISTING", ACCOUNT, PN,
                        VERIFIED_AT, "TWILIO", e164)));
        assertNull(source(id));
        assertEquals(0L, runtimeJdbc.queryForObject("""
                SELECT COUNT(*) FROM phone_number WHERE id = ?
                """, Long.class, id));
    }

    @Test
    void onlySystemMayEditAnEnrolledRowEvenWhenTenantOwnsIt() {
        String e164 = number();
        UUID id = insertLegacy(tenantA, e164, null, true);
        assertEquals(1, promoteSystem(id, "VERIFIED_EXISTING",
                ACCOUNT, PN, VERIFIED_AT, "TWILIO", e164));
        assertThrows(DataAccessException.class, () ->
                databaseContext.callAsTenant(tenantA, () ->
                        runtimeJdbc.update(
                                "UPDATE phone_number SET active = FALSE WHERE id = ?", id)));
        assertThrows(DataAccessException.class, () ->
                databaseContext.callAsTenant(tenantA, () ->
                        runtimeJdbc.update(
                                "UPDATE phone_number SET twilio_binding_source = NULL, twilio_account_sid = NULL, voice_verified_at = NULL WHERE id = ?", id)));
        assertThrows(DataAccessException.class, () -> owner.update(
                "UPDATE phone_number SET active = FALSE WHERE id = ?", id));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?", Boolean.class, id)));
        assertEquals(1, databaseContext.callAsSystem(() ->
                runtimeJdbc.update("UPDATE phone_number SET active = FALSE WHERE id = ?", id)));
    }

    @Test
    void tenantAndOwnerCannotDeleteVerifiedExistingButSystemCanAndLegacyDeleteStillWorks() {
        UUID verified = insertLegacy(tenantA, number(), null, true);
        String verifiedE164 = owner.queryForObject(
                "SELECT phone_number FROM phone_number WHERE id = ?", String.class, verified);
        assertEquals(1, promoteSystem(verified, "VERIFIED_EXISTING",
                ACCOUNT, PN, VERIFIED_AT, "TWILIO", verifiedE164));

        assertThrows(DataAccessException.class, () ->
                databaseContext.callAsTenant(tenantA, () ->
                        runtimeJdbc.update("DELETE FROM phone_number WHERE id = ?", verified)));
        assertEquals(0, databaseContext.callAsTenant(tenantB, () ->
                runtimeJdbc.update("DELETE FROM phone_number WHERE id = ?", verified)));
        assertThrows(DataAccessException.class, () ->
                owner.update("DELETE FROM phone_number WHERE id = ?", verified));
        assertEquals(1L, count(verified));
        assertEquals(1, databaseContext.callAsSystem(() ->
                runtimeJdbc.update("DELETE FROM phone_number WHERE id = ?", verified)));
        assertEquals(0L, count(verified));

        UUID legacy = insertLegacy(tenantA, number(), null, true);
        assertEquals(1, databaseContext.callAsTenant(tenantA, () ->
                runtimeJdbc.update("DELETE FROM phone_number WHERE id = ?", legacy)));
        assertEquals(0L, count(legacy));
    }

    @Test
    void tenantCannotInsertFakeVerifiedExistingEvenForItsOwnBusiness() {
        UUID id = UUID.randomUUID();
        String e164 = number();
        assertThrows(DataAccessException.class, () ->
                databaseContext.callAsTenant(tenantA, () -> runtimeJdbc.update("""
                        INSERT INTO phone_number (
                            id, business_id, phone_number, provider, external_id,
                            twilio_binding_source, twilio_account_sid, voice_verified_at, active
                        ) VALUES (?, ?, ?, 'TWILIO', ?, 'VERIFIED_EXISTING', ?, ?, FALSE)
                        """, id, tenantA, e164, PN, ACCOUNT, Timestamp.from(VERIFIED_AT))));
        assertEquals(0L, count(id));
        assertThrows(DataAccessException.class, () -> owner.update("""
                INSERT INTO phone_number (
                    id, business_id, phone_number, provider, external_id,
                    twilio_binding_source, twilio_account_sid, voice_verified_at, active
                ) VALUES (?, ?, ?, 'TWILIO', ?, 'VERIFIED_EXISTING', ?, ?, FALSE)
                """, id, tenantA, e164, PN, ACCOUNT, Timestamp.from(VERIFIED_AT)));
        assertEquals(0L, count(id));
    }
}
