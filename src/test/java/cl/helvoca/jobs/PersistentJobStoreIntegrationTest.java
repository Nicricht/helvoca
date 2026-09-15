package cl.helvoca.jobs;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PersistentJobStoreIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.jobs.enabled", () -> "false");
    }

    @Autowired PersistentJobStore store;
    @Autowired BusinessRepository businesses;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDurableJobs() {
        jdbc.update("DELETE FROM persistent_job");
    }

    @Test
    void enqueueIsTenantScopedAndIdempotent() {
        Business business = business("Job idempotency");

        PersistentJob first = store.enqueue(
                business.getId(), null, PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                "dispatch:one", "{\"messageId\":\"" + UUID.randomUUID() + "\"}", 5, Instant.now());
        PersistentJob replay = store.enqueue(
                business.getId(), null, PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                "dispatch:one", "{\"messageId\":\"" + UUID.randomUUID() + "\"}", 5, Instant.now());

        assertEquals(first.id(), replay.id());
        assertEquals(PersistentJob.Status.PENDING, replay.status());
        assertEquals(0, replay.attemptCount());
    }

    @Test
    void concurrentWorkersUseSkipLockedAndClaimDifferentJobs() throws Exception {
        Business business = business("Concurrent job workers");
        PersistentJob a = enqueue(business.getId(), "dispatch:a", 5);
        PersistentJob b = enqueue(business.getId(), "dispatch:b", 5);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PersistentJob> first = executor.submit(() -> claim("worker-a", ready, fire));
            Future<PersistentJob> second = executor.submit(() -> claim("worker-b", ready, fire));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            PersistentJob claimedA = first.get(5, TimeUnit.SECONDS);
            PersistentJob claimedB = second.get(5, TimeUnit.SECONDS);
            assertNotNull(claimedA);
            assertNotNull(claimedB);
            assertNotEquals(claimedA.id(), claimedB.id());
            assertEquals(Set.of(a.id(), b.id()), Set.of(claimedA.id(), claimedB.id()));
            assertEquals(PersistentJob.Status.RUNNING, claimedA.status());
            assertEquals(PersistentJob.Status.RUNNING, claimedB.status());
        } finally {
            fire.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsRecoveredByAnotherWorker() {
        Business business = business("Lease recovery");
        PersistentJob queued = enqueue(business.getId(), "dispatch:recover", 2);
        PersistentJob first = store.claimNext("worker-one", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(queued.id(), first.id());
        assertEquals(1, first.attemptCount());

        expireLease(first.id());

        PersistentJob recovered = store.claimNext("worker-two", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(first.id(), recovered.id());
        assertEquals("worker-two", recovered.leaseOwner());
        assertEquals(2, recovered.attemptCount());
    }

    @Test
    void expiredFinalLeaseMovesJobToDeadLetter() {
        Business business = business("Dead letter lease");
        PersistentJob queued = enqueue(business.getId(), "dispatch:dead", 1);
        PersistentJob first = store.claimNext("worker-one", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(queued.id(), first.id());
        assertEquals(1, first.attemptCount());

        expireLease(first.id());

        assertTrue(store.claimNext("worker-two", Duration.ofSeconds(30)).isEmpty());
        PersistentJob dead = store.findById(business.getId(), first.id()).orElseThrow();
        assertEquals(PersistentJob.Status.DEAD_LETTER, dead.status());
        assertEquals("LEASE_EXHAUSTED", dead.lastErrorCode());
        assertNotNull(dead.completedAt());
    }

    private PersistentJob claim(String worker, CountDownLatch ready, CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(5, TimeUnit.SECONDS);
        return store.claimNext(worker, Duration.ofSeconds(30)).orElse(null);
    }

    private PersistentJob enqueue(UUID businessId, String key, int maxAttempts) {
        return store.enqueue(
                businessId, null, PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                key, "{\"messageId\":\"" + UUID.randomUUID() + "\"}", maxAttempts, Instant.now());
    }

    private void expireLease(UUID jobId) {
        jdbc.update("UPDATE persistent_job SET lease_expires_at = NOW() - INTERVAL '1 second' WHERE id = ?", jobId);
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }
}
