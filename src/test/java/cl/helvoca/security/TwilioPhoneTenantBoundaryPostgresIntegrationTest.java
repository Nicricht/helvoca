package cl.helvoca.security;

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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part 3/10: real PostgreSQL 18 RLS, role/connection boundaries and protected
 * Twilio enrollment evidence. Synthetic data only. Never contacts a carrier.
 */
@Testcontainers
@SpringBootTest
class TwilioPhoneTenantBoundaryPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
    private static final AtomicInteger NEXT = new AtomicInteger(7000);

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", postgres::getJdbcUrl);
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
        props.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        props.add("spring.datasource.hikari.minimum-idle", () -> "1");
        props.add("spring.flyway.enabled", () -> "true");
        props.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        props.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired JdbcTemplate runtime;
    @Autowired TenantDatabaseContext scope;
    private JdbcTemplate owner;
    private UUID tenantA;
    private UUID tenantB;
    private UUID phoneA;
    private UUID phoneB;

    @BeforeEach
    void syntheticData() {
        owner = new JdbcTemplate(migrationDataSource);
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        phoneA = UUID.randomUUID();
        phoneB = UUID.randomUUID();
        owner.update("INSERT INTO public.business(id, name) VALUES (?, ?)",
                tenantA, "Part3 synthetic A");
        owner.update("INSERT INTO public.business(id, name) VALUES (?, ?)",
                tenantB, "Part3 synthetic B");
        insert(phoneA, tenantA);
        insert(phoneB, tenantB);
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
    }

    private void insert(UUID id, UUID tenant) {
        String suffix = String.format("%04d", NEXT.getAndIncrement());
        owner.update("""
                INSERT INTO public.phone_number
                    (id, business_id, phone_number, provider, active, external_id)
                VALUES (?, ?, ?, 'TWILIO', TRUE, ?)
                """, id, tenant, "+1202555" + suffix,
                "PN" + id.toString().replace("-", ""));
    }

    private long visible(UUID tenant) {
        return scope.callAsTenant(tenant, () -> runtime.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                 WHERE id IN (?,?)
                """, Long.class, phoneA, phoneB));
    }

    @Test
    void eachTenantSeesOnlyItsPhoneAndCannotReadOrAlterAnotherTenantsBinding() {
        assertEquals(1L, visible(tenantA));
        assertEquals(1L, visible(tenantB));
        assertEquals(2L, scope.callAsSystem(() -> runtime.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                 WHERE id IN (?,?)
                """, Long.class, phoneA, phoneB)));
        assertEquals(0L, runtime.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                 WHERE id IN (?,?)
                """, Long.class, phoneA, phoneB));

        assertEquals(0L, scope.callAsTenant(tenantA, () -> runtime.queryForObject(
                "SELECT COUNT(*) FROM public.phone_number WHERE id = ?",
                Long.class, phoneB)));
        assertEquals(0, scope.callAsTenant(tenantA, () -> runtime.update("""
                UPDATE public.phone_number
                   SET external_id = ?
                 WHERE id = ?
                """, "PN" + "a".repeat(32), phoneB)));
        assertEquals(0, scope.callAsTenant(tenantA, () -> runtime.update(
                "DELETE FROM public.phone_number WHERE id = ?", phoneB)));
        assertEquals(1L, owner.queryForObject(
                "SELECT COUNT(*) FROM public.phone_number WHERE id = ?",
                Long.class, phoneB));
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
    }

    @Test
    void tenantCannotForgeAnotherBusinessIdOrMoveItsPhoneAcrossTenants() {
        UUID forgedPhone = UUID.randomUUID();
        assertThrows(DataAccessException.class, () -> scope.callAsTenant(
                tenantA, () -> runtime.update("""
                        INSERT INTO public.phone_number
                           (id, business_id, phone_number, provider, active)
                        VALUES (?, ?, ?, 'TWILIO', TRUE)
                        """, forgedPhone, tenantB, "+12025557777")));
        assertThrows(DataAccessException.class, () -> scope.callAsTenant(
                tenantA, () -> runtime.update("""
                        UPDATE public.phone_number SET business_id = ?
                         WHERE id = ?
                        """, tenantB, phoneA)));
        assertEquals(0L, owner.queryForObject(
                "SELECT COUNT(*) FROM public.phone_number WHERE id = ?",
                Long.class, forgedPhone));
        assertEquals(tenantA, owner.queryForObject(
                "SELECT business_id FROM public.phone_number WHERE id = ?",
                UUID.class, phoneA));
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
    }

    @Test
    void allTwilioPlatformEvidenceTablesDenyBothTenantReadAndMutation() {
        for (String table : new String[] {
                "twilio_existing_enrollment_claim",
                "twilio_existing_enrollment_request",
                "twilio_existing_enrollment_audit" }) {
            assertEquals(1L, owner.queryForObject("""
                    SELECT COUNT(*) FROM pg_class
                    WHERE oid = (? || '')::regclass
                    AND relrowsecurity AND relforcerowsecurity
                    """, Long.class, "public." + table));
            assertEquals(0L, owner.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.role_table_grants
                    WHERE table_schema = 'public' AND table_name = ?
                      AND grantee = 'helvoca_runtime'
                    """, Long.class, table));

            assertThrows(DataAccessException.class, () -> scope.callAsTenant(
                    tenantA, () -> runtime.queryForObject(
                            "SELECT COUNT(*) FROM public." + table, Long.class)));
            assertThrows(DataAccessException.class, () -> scope.callAsTenant(
                    tenantB, () -> runtime.update("DELETE FROM public." + table)));
            assertEquals(0L, scope.callAsSystem(() -> runtime.queryForObject(
                    "SELECT COUNT(*) FROM public." + table, Long.class)));
            assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
        }
    }

    @Test
    void runtimeNeverEscalatesToSystemRoleAfterSwitchingTenantScopes() {
        assertEquals("helvoca_runtime", scope.callAsTenant(tenantA,
                () -> runtime.queryForObject("SELECT current_user", String.class)));
        assertEquals("helvoca_system", scope.callAsSystem(
                () -> runtime.queryForObject("SELECT current_user", String.class)));
        assertEquals("helvoca_runtime", runtime.queryForObject(
                "SELECT current_user", String.class));
        assertEquals(0L, runtime.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number WHERE id IN (?,?)
                """, Long.class, phoneA, phoneB));

        // A nested SYSTEM scope must restore the caller's restricted tenant,
        // and the outer close must restore fail-denied state.
        scope.runAsTenant(tenantA, () -> {
            assertEquals(1L, visible(tenantA));
            assertEquals(2L, scope.callAsSystem(() -> runtime.queryForObject("""
                    SELECT COUNT(*) FROM public.phone_number
                     WHERE id IN (?,?)
                    """, Long.class, phoneA, phoneB)));
            assertEquals(1L, visible(tenantA));
        });
        assertEquals(TenantDatabaseContext.Mode.DENIED, scope.currentOrDenied().mode());
    }

    @Test
    void pooledConcurrentTenantReadsNeverCrossContaminatePhoneVisibility()
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> probeMany(tenantA, phoneA, phoneB, start));
            Future<Boolean> b = executor.submit(() -> probeMany(tenantB, phoneB, phoneA, start));
            start.countDown();
            assertTrue(a.get(35, TimeUnit.SECONDS),
                    "Tenant A must never see the other company's phone");
            assertTrue(b.get(35, TimeUnit.SECONDS),
                    "Tenant B must never see the other company's phone");
        }
        assertEquals(0L, runtime.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number WHERE id IN (?,?)
                """, Long.class, phoneA, phoneB));
    }

    private boolean probeMany(UUID tenant, UUID own, UUID foreign,
                              CountDownLatch start) throws InterruptedException {
        start.await();
        for (int attempt = 0; attempt < 12; attempt++) {
            boolean ok = scope.callAsTenant(tenant, () ->
                    runtime.queryForObject(
                            "SELECT COUNT(*) FROM public.phone_number WHERE id = ?",
                            Long.class, own) == 1L
                    && runtime.queryForObject(
                            "SELECT COUNT(*) FROM public.phone_number WHERE id = ?",
                            Long.class, foreign) == 0L);
            if (!ok || scope.currentOrDenied().mode() != TenantDatabaseContext.Mode.DENIED) {
                return false;
            }
        }
        return true;
    }
}
