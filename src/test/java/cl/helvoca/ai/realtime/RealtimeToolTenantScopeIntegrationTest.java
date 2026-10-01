package cl.helvoca.ai.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.jobs.enabled=false"
})
class RealtimeToolTenantScopeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired RealtimeToolService tools;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;

    @Test
    void agentGreetingReadsOnlyTheBusinessFromRealtimeContextWithoutAmbientSystemScope() {
        JdbcTemplate owner = new JdbcTemplate(migrationDataSource);
        UUID businessId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        owner.update("""
                INSERT INTO business(id, name, timezone, language)
                VALUES (?, 'Realtime Tenant', 'America/Santiago', 'es')
                """, businessId);
        owner.update("""
                INSERT INTO ai_agent(id, business_id, name, language, greeting, active)
                VALUES (?, ?, 'RecepVoz', 'es', 'Saludo aislado por tenant', TRUE)
                """, agentId, businessId);

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(),
                businessId,
                null,
                "+56911111111",
                "+56222222222",
                "MZ-tenant-scope");

        assertEquals(
                "Saludo aislado por tenant",
                tools.agentGreeting(context, "fallback"));
    }
}
