package cl.helvoca.telephony.twilio;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

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
}
