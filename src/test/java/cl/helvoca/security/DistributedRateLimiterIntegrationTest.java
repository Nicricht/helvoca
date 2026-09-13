package cl.helvoca.security;

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
