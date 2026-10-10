package cl.helvoca.security;

import cl.helvoca.onboarding.BusinessImportAiBudget;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class DistributedRateLimiterIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;

    @Test
    void twoLimiterInstancesShareOneAtomicTenantBudget() throws Exception {
        jdbc.update("DELETE FROM api_rate_limit_bucket");
        DistributedRateLimiter replicaA = new DistributedRateLimiter(jdbc);
        DistributedRateLimiter replicaB = new DistributedRateLimiter(jdbc);

        int attempts = 25;
        int limit = 10;
        Instant sameWindow = Instant.ofEpochSecond(1_789_300_000L);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                DistributedRateLimiter replica = (i % 2 == 0) ? replicaA : replicaB;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(fire.await(5, TimeUnit.SECONDS));
                    return replica.consume("authenticated:tenant-hash", limit, 60, sameWindow).allowed();
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) allowed++;
            }

            assertEquals(limit, allowed);
            Integer stored = jdbc.queryForObject(
                    "SELECT request_count FROM api_rate_limit_bucket WHERE bucket_key = ?",
                    Integer.class, "authenticated:tenant-hash");
            assertEquals(attempts, stored);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    void paidImportQuotaDoesNotCrossTenantAndHandlesConcurrentRequests() throws Exception {
        // Reuse the real RLS/Flyway-backed PostgreSQL limiter; no external AI calls.
        jdbc.update("DELETE FROM api_rate_limit_bucket WHERE bucket_key LIKE 'business-import-ai:%'");
        DistributedRateLimiter shared = new DistributedRateLimiter(jdbc);

        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        TenantProvider tenantOne = mock(TenantProvider.class);
        TenantProvider tenantTwo = mock(TenantProvider.class);
        when(tenantOne.requireBusinessId()).thenReturn(one);
        when(tenantTwo.requireBusinessId()).thenReturn(two);
        BusinessImportAiBudget a = new BusinessImportAiBudget(shared, tenantOne);
        BusinessImportAiBudget b = new BusinessImportAiBudget(shared, tenantTwo);
        ReflectionTestUtils.setField(a, "maxAttempts", 2);
        ReflectionTestUtils.setField(b, "maxAttempts", 2);

        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                final BusinessImportAiBudget selected = i < 9 ? a : b;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(fire.await(5, TimeUnit.SECONDS));
                    return selected.reserve();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();
            int allowedFirst = 0;
            int allowedSecond = 0;
            for (int i = 0; i < results.size(); i++) {
                boolean allowed = results.get(i).get(15, TimeUnit.SECONDS);
                if (allowed && i < 9) allowedFirst++;
                if (allowed && i >= 9) allowedSecond++;
            }
            assertEquals(2, allowedFirst);
            assertEquals(2, allowedSecond);
            assertEquals(9, jdbc.queryForObject(
                    "SELECT request_count FROM api_rate_limit_bucket WHERE bucket_key = ?",
                    Integer.class, "business-import-ai:" + one));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT request_count FROM api_rate_limit_bucket WHERE bucket_key = ?",
                    Integer.class, "business-import-ai:" + two));
        } finally {
            executor.shutdownNow();
        }
    }

}
