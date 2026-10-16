package cl.helvoca.phone;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A real PostgreSQL 18 Flyway UPGRADE from a populated V99 database to V103.
 * Stage B separately exercised (a) clean Flyway schemas and (b) psql on a
 * locally restored production copy. This test closes the missing combination:
 * Flyway incremental upgrade and legacy row conservation, with synthetic data.
 *
 * No Railway, carrier, network-to-production, pilot or actual phone call.
 */
@Testcontainers
class TwilioV99ToV103FlywayUpgradePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static Flyway migrations(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static final String PHONE_INVARIANT = """
            SELECT md5(COALESCE(string_agg(
              (to_jsonb(p) - 'twilio_binding_source'
                           - 'twilio_account_sid'
                           - 'voice_verified_at')::text,
              ',' ORDER BY p.id), ''))
            FROM public.phone_number p
            """;

    @Test
    void flywayUpgradesPopulatedLegacyV99ToV103WithoutMutatingExistingPhoneOrTenant(
            @TempDir Path legacyMigrationDirectory) throws IOException {
        Flyway oldFlyway = migrations(MigrationVersion.fromVersion("99"));
        MigrateResult prepared = oldFlyway.migrate();
        assertTrue(prepared.success, "isolated V1-V99 baseline must migrate");

        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.flyway_schema_history
                WHERE version = '99' AND success
                """, Long.class));
        assertEquals(0L, owner.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'phone_number'
                  AND column_name IN
                      ('twilio_binding_source', 'twilio_account_sid', 'voice_verified_at')
                """, Long.class));

        UUID tenant = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        String legacySid = "PN" + "c".repeat(32);
        owner.update("INSERT INTO public.business(id, name) VALUES (?, ?)",
                tenant, "V99 to V103 disposable upgrade tenant");
        owner.update("""
                INSERT INTO public.phone_number
                     (id, business_id, phone_number, provider, external_id, active)
                VALUES (?, ?, ?, 'TWILIO', ?, TRUE)
                """, phoneId, tenant, "+12025550199", legacySid);

        String before = owner.queryForObject(PHONE_INVARIANT, String.class);
        Long businessBefore = owner.queryForObject(
                "SELECT COUNT(*) FROM public.business", Long.class);
        Long numbersBefore = owner.queryForObject(
                "SELECT COUNT(*) FROM public.phone_number", Long.class);
        Integer version99Checksum = owner.queryForObject("""
                SELECT checksum FROM public.flyway_schema_history
                WHERE version = '99' AND success
                """, Integer.class);

        Flyway finalFlyway = migrations(MigrationVersion.LATEST);
        MigrateResult upgraded = finalFlyway.migrate();
        assertTrue(upgraded.success, "Flyway incremental migration should succeed");
        assertEquals(4, upgraded.migrationsExecuted,
                "Only V100, V101, V102 and V103 should run after V99");
        finalFlyway.validate();

        // C2-B rollback boundary: run the *legacy* V1..V99 Flyway catalog
        // against the already upgraded schema. This isolates Flyway's future-
        // migration validation behavior without starting old application code.
        // The PR's only changed migrations are V100..V103, preserving V1..V99.
        Pattern oldMigration = Pattern.compile("V(?:[1-9]|[1-9][0-9])__.*[.]sql");
        Path checkedInMigrations = Path.of("src/main/resources/db/migration");
        int oldCount = 0;
        try (var migrationPaths = Files.list(checkedInMigrations)) {
            for (Path sql : migrationPaths
                    .filter(path -> oldMigration.matcher(path.getFileName().toString()).matches())
                    .toList()) {
                Files.copy(sql, legacyMigrationDirectory.resolve(sql.getFileName()));
                oldCount++;
            }
        }
        Integer appliedLegacyCount = owner.queryForObject("""
                SELECT COUNT(*) FROM public.flyway_schema_history
                WHERE version IS NOT NULL AND version::integer <= 99 AND success
                """, Integer.class);
        assertEquals(appliedLegacyCount.intValue(), oldCount,
                "Legacy V1..V99 catalog must exactly match successful recorded migrations");

        Flyway legacyCatalog = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + legacyMigrationDirectory.toAbsolutePath())
                .load();
        assertDoesNotThrow(legacyCatalog::validate,
                "V99-only Flyway migrations must validate on V103 without schema rollback");
        assertEquals(103L, owner.queryForObject(
                "SELECT MAX(version::integer) FROM public.flyway_schema_history WHERE success",
                Long.class), "Legacy Flyway validation must NOT roll back the upgraded schema");


        assertEquals(before, owner.queryForObject(PHONE_INVARIANT, String.class),
                "Existing phone rows and metadata must not change");
        assertEquals(businessBefore, owner.queryForObject(
                "SELECT COUNT(*) FROM public.business", Long.class));
        assertEquals(numbersBefore, owner.queryForObject(
                "SELECT COUNT(*) FROM public.phone_number", Long.class));
        assertEquals(version99Checksum, owner.queryForObject("""
                SELECT checksum FROM public.flyway_schema_history
                WHERE version = '99' AND success
                """, Integer.class), "V99 recorded checksum must remain untouched");

        assertEquals(4L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.flyway_schema_history
                WHERE version IN ('100', '101', '102', '103') AND success
                """, Long.class));
        assertEquals(0L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.flyway_schema_history WHERE NOT success
                """, Long.class));

        assertEquals(3L, owner.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='phone_number'
                  AND column_name IN
                    ('twilio_binding_source','twilio_account_sid','voice_verified_at')
                """, Long.class));
        assertEquals(0L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                WHERE id = ? AND (twilio_binding_source IS NOT NULL
                    OR twilio_account_sid IS NOT NULL OR voice_verified_at IS NOT NULL)
                """, Long.class, phoneId),
                "A legacy number must NOT be automatically certified or backfilled");

        for (String platformTable : new String[] {
                "twilio_existing_enrollment_claim",
                "twilio_existing_enrollment_request",
                "twilio_existing_enrollment_audit" }) {
            assertEquals(1L, owner.queryForObject("""
                    SELECT COUNT(*) FROM pg_class
                    WHERE oid = (? || '')::regclass
                      AND relrowsecurity AND relforcerowsecurity
                    """, Long.class, "public." + platformTable),
                    "Platform-only table must have FORCE RLS: " + platformTable);
            assertEquals(0L, owner.queryForObject(
                    "SELECT COUNT(*) FROM public." + platformTable, Long.class),
                    "Migration must never create approval claims: " + platformTable);
        }

        assertEquals(2L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_trigger
                WHERE tgname IN
                    ('trg_phone_verified_existing_write_guard',
                     'trg_twilio_existing_request_immutable_guard')
                  AND NOT tgisinternal
                """, Long.class));
        assertThrows(DataAccessException.class, () -> owner.update("""
                UPDATE public.phone_number SET twilio_binding_source='VERIFIED_EXISTING'
                WHERE id = ?
                """, phoneId), "A raw UPDATE must not confer carrier provenance");
        assertEquals(0L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                WHERE id = ? AND twilio_binding_source IS NOT NULL
                """, Long.class, phoneId));
    }
}
