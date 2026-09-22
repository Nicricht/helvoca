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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PostgresRowLevelSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway can hold one connection while opening another migration connection.
        // Two connections avoid startup starvation while still letting the scrub test
        // borrow the complete runtime pool simultaneously.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired JdbcTemplate runtimeJdbc;
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    JdbcTemplate ownerJdbc;

    private UUID businessA;
    private UUID businessB;

    @BeforeEach
    void seed() {
        ownerJdbc = new JdbcTemplate(migrationDataSource);

        ownerJdbc.update("DELETE FROM audit_log");
        ownerJdbc.update("DELETE FROM user_role");
        ownerJdbc.update("DELETE FROM app_user");
        ownerJdbc.update("DELETE FROM customer");
        ownerJdbc.update("DELETE FROM business");

        businessA = UUID.randomUUID();
        businessB = UUID.randomUUID();
        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessA, "Tenant A");
        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessB, "Tenant B");

        ownerJdbc.update("INSERT INTO customer(id, business_id, name, phone) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), businessA, "Customer A", "+56911111111");
        ownerJdbc.update("INSERT INTO customer(id, business_id, name, phone) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), businessB, "Customer B", "+56922222222");

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        ownerJdbc.update("INSERT INTO app_user(id, business_id, name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                userA, businessA, "Admin A", "a-" + userA + "@example.test", "hash");
        ownerJdbc.update("INSERT INTO app_user(id, business_id, name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                userB, businessB, "Admin B", "b-" + userB + "@example.test", "hash");

        UUID roleId = ownerJdbc.queryForObject("SELECT id FROM role ORDER BY code LIMIT 1", UUID.class);
        assertNotNull(roleId, "baseline migrations must seed at least one role");
        ownerJdbc.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", userA, roleId);
        ownerJdbc.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", userB, roleId);
    }

    @Test
    void auditLogIsTenantIsolatedAndApplicationAppendOnly() {
        UUID auditA = UUID.randomUUID();
        UUID auditB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result) VALUES (?, ?, ?, ?)",
                auditA, businessA, "AUDIT_A", "SUCCESS");
        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result) VALUES (?, ?, ?, ?)",
                auditB, businessB, "AUDIT_B", "SUCCESS");

        long visibleA = databaseContext.callAsTenant(businessA,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class));
        long visibleB = databaseContext.callAsTenant(businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class));

        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);

        databaseContext.runAsTenant(businessA, () ->
                runtimeJdbc.update(
                        "INSERT INTO audit_log(id, business_id, action, result) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(), businessA, "TENANT_INSERT", "SUCCESS"));

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(businessA, () ->
                runtimeJdbc.update("UPDATE audit_log SET action = ? WHERE id = ?", "MUTATED", auditA)));

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(businessA, () ->
                runtimeJdbc.update("DELETE FROM audit_log WHERE id = ?", auditA)));

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(businessA, () ->
                runtimeJdbc.update(
                        "INSERT INTO audit_log(id, business_id, action, result) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(), businessB, "CROSS_TENANT", "SUCCESS")));

        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.audit_log', 'UPDATE')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.audit_log', 'DELETE')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_system', 'public.audit_log', 'UPDATE')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_system', 'public.audit_log', 'DELETE')",
                Boolean.class));
    }

    @Test
    void auditRetentionFunctionDeletesOnlyExpiredRowsForActiveTenant() {
        UUID oldAuditA = UUID.randomUUID();
        UUID recentAuditA = UUID.randomUUID();
        UUID oldAuditB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result, created_at) VALUES (?, ?, ?, ?, NOW() - INTERVAL '25 months')",
                oldAuditA, businessA, "OLD_A", "SUCCESS");
        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result, created_at) VALUES (?, ?, ?, ?, NOW() - INTERVAL '23 months')",
                recentAuditA, businessA, "RECENT_A", "SUCCESS");
        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result, created_at) VALUES (?, ?, ?, ?, NOW() - INTERVAL '25 months')",
                oldAuditB, businessB, "OLD_B", "SUCCESS");

        Integer deleted = databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT public.purge_expired_audit_log_for_current_tenant()",
                        Integer.class));

        assertEquals(1, deleted);
        assertEquals(0L, ownerJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE id = ?",
                Long.class,
                oldAuditA));
        assertEquals(1L, ownerJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE id = ?",
                Long.class,
                recentAuditA));
        assertEquals(1L, ownerJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE id = ?",
                Long.class,
                oldAuditB));

        assertThrows(DataAccessException.class, () -> {
            try (TenantDatabaseContext.Scope ignored = databaseContext.deny()) {
                runtimeJdbc.queryForObject(
                        "SELECT public.purge_expired_audit_log_for_current_tenant()",
                        Integer.class);
            }
        });

        assertTrue(ownerJdbc.queryForObject(
                "SELECT has_function_privilege('helvoca_runtime', 'public.purge_expired_audit_log_for_current_tenant()', 'EXECUTE')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_function_privilege('helvoca_system', 'public.purge_expired_audit_log_for_current_tenant()', 'EXECUTE')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.audit_log', 'DELETE')",
                Boolean.class));
    }

    @Test
    void tenantReadWithoutBusinessPredicateCannotSeeAnotherTenant() {
        long visibleA = databaseContext.callAsTenant(businessA,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class));
        long visibleB = databaseContext.callAsTenant(businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class));

        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);
    }

    @Test
    void tenantCannotInsertRowOwnedByAnotherTenant() {
        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(businessA, () ->
                runtimeJdbc.update("INSERT INTO customer(id, business_id, name) VALUES (?, ?, ?)",
                        UUID.randomUUID(), businessB, "cross-tenant-write")));

        assertEquals(1L, databaseContext.callAsTenant(businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class)));
    }

    @Test
    void derivedChildTableWithoutBusinessIdIsStillTenantIsolated() {
        long rolesA = databaseContext.callAsTenant(businessA,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM user_role", Long.class));
        long rolesB = databaseContext.callAsTenant(businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM user_role", Long.class));

        assertEquals(1L, rolesA);
        assertEquals(1L, rolesB);
    }

    @Test
    void deniedContextFailsClosedWhileSystemScopeCanSeeBothTenants() {
        long denied;
        try (TenantDatabaseContext.Scope ignored = databaseContext.deny()) {
            denied = runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class);
        }
        long system = databaseContext.callAsSystem(
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class));

        assertEquals(0L, denied);
        assertEquals(2L, system);
    }

    @Test
    void pooledPhysicalConnectionsAreScrubbedBetweenTenantAndOwnerUse() throws Exception {
        String tenantRole = databaseContext.callAsTenant(businessA,
                () -> runtimeJdbc.queryForObject("SELECT current_user", String.class));
        String tenantSetting = databaseContext.callAsTenant(businessA,
                () -> runtimeJdbc.queryForObject("SELECT current_setting('app.tenant_id', true)", String.class));

        assertEquals(TenantAwareDataSource.TENANT_ROLE, tenantRole);
        assertEquals(businessA.toString(), tenantSetting);

        // Borrow the whole two-connection pool at once. The connection used by the
        // tenant-aware datasource must therefore be one of these physical sessions,
        // and every returned session must already have been scrubbed.
        try (Connection first = migrationDataSource.getConnection();
             Connection second = migrationDataSource.getConnection()) {
            assertOwnerSessionScrubbed(first);
            assertOwnerSessionScrubbed(second);
        }

        long visibleB = databaseContext.callAsTenant(businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class));
        assertEquals(1L, visibleB);
    }

    private void assertOwnerSessionScrubbed(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT current_user, current_setting('app.tenant_id', true)")) {
            assertTrue(result.next());
            String ownerRole = result.getString(1);
            String ownerTenantSetting = result.getString(2);

            assertNotEquals(TenantAwareDataSource.TENANT_ROLE, ownerRole);
            assertNotEquals(TenantAwareDataSource.SYSTEM_ROLE, ownerRole);
            assertTrue(ownerTenantSetting == null || ownerTenantSetting.isBlank());
        }
    }

    @Test
    void schemaGuardRejectsUnprotectedTenantTablesAndDirectTenantChildren() {
        List<String> directTenantTablesWithoutRls = ownerJdbc.queryForList("""
                SELECT c.table_name
                  FROM information_schema.columns c
                  JOIN information_schema.tables t
                    ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                  JOIN pg_class pc ON pc.relname = c.table_name
                  JOIN pg_namespace pn ON pn.oid = pc.relnamespace AND pn.nspname = c.table_schema
                 WHERE c.table_schema = 'public'
                   AND c.column_name = 'business_id'
                   AND c.udt_name = 'uuid'
                   AND t.table_type = 'BASE TABLE'
                   AND NOT pc.relrowsecurity
                 ORDER BY c.table_name
                """, String.class);
        assertTrue(directTenantTablesWithoutRls.isEmpty(),
                "Tables with business_id must have RLS: " + directTenantTablesWithoutRls);

        List<String> directChildrenWithoutRls = ownerJdbc.queryForList("""
                SELECT DISTINCT child.relname
                  FROM pg_constraint fk
                  JOIN pg_class child ON child.oid = fk.conrelid
                  JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
                  JOIN pg_class parent ON parent.oid = fk.confrelid
                  JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
                 WHERE fk.contype = 'f'
                   AND child_ns.nspname = 'public'
                   AND parent_ns.nspname = 'public'
                   AND parent.relrowsecurity = TRUE
                   AND child.relkind = 'r'
                   AND child.relname <> 'flyway_schema_history'
                   AND child.relrowsecurity = FALSE
                 ORDER BY child.relname
                """, String.class);
        assertTrue(directChildrenWithoutRls.isEmpty(),
                "Direct children of RLS tenant tables must also have RLS: " + directChildrenWithoutRls);
    }
}
