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
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

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
    void concurrentWorkersCannotReserveMoreThanTwoAttempts() throws Exception {
        UUID demo = UUID.randomUUID();
        new JdbcTemplate(migrationDataSource).update(
                "INSERT INTO business(id,name,mode) VALUES (?,'Synthetic quota demo','DEMO')", demo);
        for (int i = 0; i < 3; i++) {
            String runId = "quota-" + UUID.randomUUID();
            assertTrue(scope.callAsSystem(() -> commands.enqueue(runId, "test", demo)));
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
            for (var claim : claims) claim.get(15, TimeUnit.SECONDS);
            while (scope.callAsSystem(commands::claimNext).isPresent()) {
                // Drain any claim skipped while another worker held the quota row.
            }
            assertEquals(2, scope.callAsSystem(() -> jdbc.queryForObject(
                    "SELECT reserved_calls FROM twilio_certification_demo_call_quota WHERE business_id = ?",
                    Integer.class, demo)));
            assertEquals(1L, scope.callAsSystem(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM twilio_certification_command WHERE business_id = ? AND status = 'PENDING'",
                    Long.class, demo)));
        } finally {
            pool.shutdownNow();
        }
    }
}
