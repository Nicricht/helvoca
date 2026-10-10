package cl.helvoca.onboarding;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "app.onboarding.import-ai.enabled=true")
class BusinessImportAiPlanQuotaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired BusinessImportAiPlanQuota quota;

    private JdbcTemplate owner;
    private UUID a;
    private UUID b;

    @BeforeEach
    void setup() {
        owner = new JdbcTemplate(migrationDataSource);
        a = UUID.randomUUID();
        b = UUID.randomUUID();
        for (UUID id : new UUID[] {a, b}) {
            owner.update("INSERT INTO public.business(id, name) VALUES (?, ?)", id, "Quota integration tenant");
            owner.update("""
                    INSERT INTO public.business_subscription
                        (id, business_id, plan_code, status, current_period_start, current_period_end, created_at, updated_at)
                    VALUES (?, ?, 'BASIC', 'ACTIVE', ?, ?, now(), now())
                    """, UUID.randomUUID(), id, Timestamp.from(Instant.now().minusSeconds(600)),
                    Timestamp.from(Instant.now().plusSeconds(600)));
        }
        owner.update("""
                UPDATE public.commercial_plan_entitlement SET limit_value = 1
                 WHERE plan_code = 'BASIC' AND entitlement_key = 'AI_IMPORT_REQUESTS'
                """);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        owner.update("""
                UPDATE public.commercial_plan_entitlement SET limit_value = 0
                 WHERE plan_code = 'BASIC' AND entitlement_key = 'AI_IMPORT_REQUESTS'
                """);
        owner.update("DELETE FROM public.business WHERE id IN (?, ?)", a, b);
    }

    private <T> T as(UUID businessId, java.util.function.Supplier<T> action) {
        Jwt jwt = Jwt.withTokenValue("integration-test")
                .header("alg", "none")
                .claim("business_id", businessId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return databaseContext.callAsTenant(businessId, action);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void tenantIsolationRealLedgerAndDuplicateAttemptCannotDoubleCount() {
        UUID attemptA = UUID.randomUUID();
        assertTrue(as(a, () -> quota.reserve(attemptA)));
        assertFalse(as(a, () -> quota.reserve(attemptA)));
        assertFalse(as(a, () -> quota.reserve(UUID.randomUUID())));
        assertTrue(as(b, () -> quota.reserve(UUID.randomUUID())));
        var aStatus = as(a, quota::current);
        var bStatus = as(b, quota::current);
        assertEquals("LIMIT_REACHED", aStatus.status());
        assertEquals("LIMIT_REACHED", bStatus.status());
        assertEquals(0, BigDecimal.ONE.compareTo(aStatus.used()));
        assertEquals(0, BigDecimal.ONE.compareTo(bStatus.used()));
        assertEquals(0, BigDecimal.ZERO.compareTo(aStatus.remaining()));
        long visibleA = databaseContext.callAsTenant(a, () -> jdbc.queryForObject(
                "SELECT count(*) FROM public.usage_meter_event WHERE meter_key = 'AI_IMPORT_REQUESTS'",
                Long.class));
        long visibleB = databaseContext.callAsTenant(b, () -> jdbc.queryForObject(
                "SELECT count(*) FROM public.usage_meter_event WHERE meter_key = 'AI_IMPORT_REQUESTS'",
                Long.class));
        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);
    }

    @Test
    void parallelInstancesCannotConsumeSameFinalSubscriptionSlot() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                ready.countDown();
                if (!go.await(15, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                return as(a, () -> quota.reserve(UUID.randomUUID()));
            });
            var second = pool.submit(() -> {
                ready.countDown();
                if (!go.await(15, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                return as(a, () -> quota.reserve(UUID.randomUUID()));
            });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(0, BigDecimal.ONE.compareTo(as(a, quota::current).used()));
    }

    @Test
    void suspendedBusinessAndExpiredSubscriptionNeverSpendProviderQuota() {
        owner.update("UPDATE public.business SET status = 'SUSPENDED' WHERE id = ?", a);
        assertEquals("BUSINESS_INACTIVE", as(a, quota::current).status());
        assertFalse(as(a, () -> quota.reserve(UUID.randomUUID())));
        owner.update("UPDATE public.business SET status = 'ACTIVE' WHERE id = ?", a);
        owner.update("UPDATE public.business_subscription SET current_period_end = ? WHERE business_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), a);
        assertEquals("PERIOD_EXPIRED", as(a, quota::current).status());
        assertFalse(as(a, () -> quota.reserve(UUID.randomUUID())));
        assertEquals(0, BigDecimal.ZERO.compareTo(as(a, quota::current).used()));
    }
}
