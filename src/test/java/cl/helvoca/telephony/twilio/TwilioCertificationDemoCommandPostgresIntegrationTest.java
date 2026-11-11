package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real Flyway/PostgreSQL migration, tenant pin, token expiry and replay. No carrier call. */
@Testcontainers
@SpringBootTest
class TwilioCertificationDemoCommandPostgresIntegrationTest {

    @Test
    void exactRunClaimLeavesOtherPendingRowsAndCannotBeClaimedTwiceConcurrently() throws Exception {
        UUID demo = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var owner = new JdbcTemplate(migrationDataSource);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Pinned demo','DEMO')", demo);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Other pinned demo','DEMO')", other);
        String target = "aurora-one-" + UUID.randomUUID();
        String older = "aurora-other-" + UUID.randomUUID();
        String foreign = "aurora-foreign-" + UUID.randomUUID();
        scope.runAsSystem(() -> {
            assertTrue(commands.enqueue(older, "startup-bootstrap", demo));
            assertTrue(commands.enqueue(target, "startup-bootstrap", demo));
            assertTrue(commands.enqueue(foreign, "startup-bootstrap", other));
        });
        assertTrue(scope.callAsSystem(() -> commands.claimExactRunId(target, other, "startup-bootstrap")).isEmpty());
        assertTrue(scope.callAsSystem(() -> commands.claimExactRunId(target, demo, "platform-admin")).isEmpty());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return scope.callAsSystem(() ->
                    commands.claimExactRunId(target, demo, "startup-bootstrap")); });
            var second = pool.submit(() -> { start.await(); return scope.callAsSystem(() ->
                    commands.claimExactRunId(target, demo, "startup-bootstrap")); });
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS).isPresent() ? 1 : 0));
            assertTrue(scope.callAsSystem(() -> commands.claimExactRunId(target, demo, "startup-bootstrap")).isEmpty());
            assertEquals("PENDING", scope.callAsSystem(() -> commands.find(older).orElseThrow().status()));
            assertEquals("PENDING", scope.callAsSystem(() -> commands.find(foreign).orElseThrow().status()));
            assertEquals("CLAIMED", scope.callAsSystem(() -> commands.find(target).orElseThrow().status()));
        } finally {
            pool.shutdownNow();
            owner.update("""
                    UPDATE twilio_certification_command
                       SET status = 'FAILED', failure_reason = 'synthetic test cleanup',
                           completed_at = CURRENT_TIMESTAMP
                     WHERE run_id IN (?, ?, ?) AND status = 'PENDING'
                    """, target, older, foreign);
        }
    }
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            System.getProperty("helvoca.test.postgres.image", "postgres:18-alpine"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired TwilioCertificationCommandStore commands;
    @Autowired TenantDatabaseContext scope;
    @Autowired JdbcTemplate jdbc;

    @Test
    void claimOnlyBoundCommandAndConsumeTokenOnceForItsTenant() {
        var owner = new JdbcTemplate(migrationDataSource);
        UUID demo = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Synthetic demo','DEMO')", demo);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Other synthetic demo','DEMO')", other);
        String legacy = "legacy-" + UUID.randomUUID();
        String bound = "aurora-" + UUID.randomUUID();
        scope.runAsSystem(() -> {
            assertTrue(commands.enqueue(legacy, "test"));
            assertTrue(commands.enqueue(bound, "test", demo));
        });
        assertEquals(1L, scope.callAsTenant(demo, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM twilio_certification_command WHERE run_id = ?", Long.class, bound)));
        assertEquals(0L, scope.callAsTenant(other, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM twilio_certification_command WHERE run_id = ?", Long.class, bound)));
        assertThrows(DataAccessException.class, () -> scope.runAsTenant(other, () ->
                commands.enqueue("foreign-" + UUID.randomUUID(), "test", demo)));

        assertEquals(0L, scope.callAsSystem(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM twilio_certification_demo_call_quota WHERE business_id = ?",
                Long.class, demo)), "No quota row is required or created for a DEMO command");
        assertFalse(scope.callAsSystem(() -> commands.enqueue(bound, "duplicate", demo)));

        var claim = scope.callAsSystem(() -> commands.claimNext().orElseThrow());
        assertEquals(bound, claim.runId());
        assertEquals(demo, claim.businessId());
        String sid = "CA" + "a".repeat(32);
        assertFalse(scope.callAsSystem(() -> commands.consumeIngressToken(claim.callbackToken(), sid, other)));
        assertTrue(scope.callAsSystem(() -> commands.consumeIngressToken(claim.callbackToken(), sid, demo)));
        assertFalse(scope.callAsSystem(() -> commands.consumeIngressToken(claim.callbackToken(), sid, demo)));
        assertEquals("PENDING", scope.callAsSystem(() -> commands.find(legacy).orElseThrow().status()));
    }

    @Test
    void databaseRejectsCommandForNonexistentTenant() {
        assertThrows(DataIntegrityViolationException.class, () -> scope.runAsSystem(() ->
                commands.enqueue("aurora-" + UUID.randomUUID(), "test", UUID.randomUUID())));
    }

    @Test
    void concurrentWorkersClaimUnlimitedExplicitAttemptsWithoutChangingHistoricalQuota() throws Exception {
        UUID demo = UUID.randomUUID();
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Synthetic unlimited demo','DEMO')", demo);
        // Historical V105 quota is exhausted; a forward-only application change must not reset it.
        owner.update("INSERT INTO twilio_certification_demo_call_quota (business_id, reserved_calls)"
                + " VALUES (?, 2)", demo);
        int totalAttempts = 12;
        for (int i = 0; i < totalAttempts; i++) {
            assertTrue(scope.callAsSystem(() ->
                    commands.enqueue("unlimited-" + UUID.randomUUID(), "operator", demo)));
        }
        var pool = Executors.newFixedThreadPool(3);
        var start = new CountDownLatch(1);
        try {
            var claims = java.util.stream.IntStream.range(0, 3)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return scope.callAsSystem(commands::claimNext);
                    })).toList();
            start.countDown();
            for (var claim : claims) {
                assertTrue(claim.get(15, TimeUnit.SECONDS).isPresent());
            }
            while (scope.callAsSystem(commands::claimNext).isPresent()) {
                // Drain distinct commands; no shared quota row or cumulative cap is consulted.
            }
            assertEquals(2, scope.callAsSystem(() -> jdbc.queryForObject(
                    "SELECT reserved_calls FROM twilio_certification_demo_call_quota WHERE business_id = ?",
                    Integer.class, demo)), "Preserve legacy quota history unchanged");
            assertEquals((long) totalAttempts, scope.callAsSystem(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM twilio_certification_command WHERE business_id = ? AND status = 'CLAIMED'",
                    Long.class, demo)));
            assertEquals(0L, scope.callAsSystem(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM twilio_certification_command WHERE business_id = ? AND status = 'PENDING'",
                    Long.class, demo)));
            String nextRunId = "unlimited-" + UUID.randomUUID();
            assertTrue(scope.callAsSystem(() -> commands.enqueue(nextRunId, "operator", demo)));
            assertEquals(nextRunId, scope.callAsSystem(() -> commands.claimNext().orElseThrow().runId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void genericWorkerNeverClaimsChatGptAuroraScopedCommand() {
        UUID demo = UUID.randomUUID();
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        owner.update("""
                UPDATE twilio_certification_command
                   SET status = 'FAILED',
                       failure_reason = 'isolated test reset',
                       completed_at = CURRENT_TIMESTAMP
                 WHERE status = 'PENDING'
                """);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Synthetic ChatGPT isolation','DEMO')", demo);
        String runId = "chatgpt-aurora-" + UUID.randomUUID();
        String actor = "chatgpt-aurora:" + UUID.randomUUID();

        assertTrue(scope.callAsSystem(() -> commands.enqueue(runId, actor, demo)));

        // The generic worker may claim another older command, but never this integration-owned command.
        scope.callAsSystem(commands::claimNext);

        assertEquals("PENDING", scope.callAsSystem(() -> commands.find(runId).orElseThrow().status()));
    }


    @Test
    void dedicatedWorkerClaimsOnlyChatGptCommandsAndRevocationCancelsOnlyPendingTenantCommands() {
        UUID demo = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Synthetic ChatGPT tenant','DEMO')", demo);
        owner.update("INSERT INTO business(id,name,mode) VALUES (?,'Other ChatGPT tenant','DEMO')", other);

        String chatGptRun = "chatgpt-aurora-" + UUID.randomUUID();
        String ordinaryRun = "ordinary-run-" + UUID.randomUUID();
        String pendingChatGptRun = "chatgpt-aurora-" + UUID.randomUUID();
        String otherTenantChatGptRun = "chatgpt-aurora-" + UUID.randomUUID();
        scope.runAsSystem(() -> {
            assertTrue(commands.enqueue(chatGptRun, "chatgpt-aurora:" + UUID.randomUUID(), demo));
            assertTrue(commands.enqueue(ordinaryRun, "platform-admin", demo));
            assertTrue(commands.enqueue(pendingChatGptRun, "chatgpt-aurora:" + UUID.randomUUID(), demo));
            assertTrue(commands.enqueue(otherTenantChatGptRun, "chatgpt-aurora:" + UUID.randomUUID(), other));
        });

        var claimed = scope.callAsSystem(() -> commands.claimNextForChatGptAurora(demo)).orElseThrow();
        assertEquals(chatGptRun, claimed.runId());
        assertEquals("PENDING", scope.callAsSystem(() -> commands.find(ordinaryRun).orElseThrow().status()));
        assertTrue(scope.callAsSystem(() -> commands.findForChatGptAurora(chatGptRun, demo)).isPresent());
        assertTrue(scope.callAsSystem(() -> commands.findForChatGptAurora(ordinaryRun, demo)).isEmpty());
        assertTrue(scope.callAsSystem(() -> commands.findForChatGptAurora(chatGptRun, other)).isEmpty());

        assertEquals(1, scope.callAsSystem(() -> commands.cancelPendingChatGptAurora(demo)));
        assertEquals("FAILED", scope.callAsSystem(() -> commands.find(pendingChatGptRun).orElseThrow().status()));
        assertEquals("PENDING", scope.callAsSystem(() -> commands.find(otherTenantChatGptRun).orElseThrow().status()));
        assertEquals("CLAIMED", scope.callAsSystem(() -> commands.find(chatGptRun).orElseThrow().status()));
    }


}
