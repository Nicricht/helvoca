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

        ownerJdbc.update("DELETE FROM retention_legal_hold");
        ownerJdbc.update("DELETE FROM audit_log");
        ownerJdbc.update("DELETE FROM user_role");
        ownerJdbc.update("DELETE FROM app_user");
        ownerJdbc.update("DELETE FROM inventory_restock_notification");
        ownerJdbc.update("DELETE FROM inventory_restock_subscription");
        ownerJdbc.update("DELETE FROM customer");
        ownerJdbc.update("UPDATE call_session SET demo_session_id = NULL WHERE demo_session_id IS NOT NULL");
        ownerJdbc.update("DELETE FROM demo_session");
        ownerJdbc.update("DELETE FROM demo_profile");
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
    void businessProfilePhoneConstraintAcceptsApiValidE164Phone() {
        ownerJdbc.update(
                "INSERT INTO business_profile(business_id, public_phone) VALUES (?, ?)",
                businessA,
                "+56975856664");

        assertEquals(
                "+56975856664",
                ownerJdbc.queryForObject(
                        "SELECT public_phone FROM business_profile WHERE business_id = ?",
                        String.class,
                        businessA));

        assertThrows(DataAccessException.class, () -> ownerJdbc.update(
                "INSERT INTO business_profile(business_id, public_phone) VALUES (?, ?)",
                businessB,
                "56975856664"));
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
    void auditRetentionFunctionDeletesOnlyExpiredUnheldRowsForActiveTenant() {
        UUID oldAuditA = UUID.randomUUID();
        UUID heldOldAuditA = UUID.randomUUID();
        UUID recentAuditA = UUID.randomUUID();
        UUID oldAuditB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result, created_at) VALUES (?, ?, ?, ?, NOW() - INTERVAL '25 months')",
                oldAuditA, businessA, "OLD_A", "SUCCESS");
        ownerJdbc.update(
                "INSERT INTO audit_log(id, business_id, action, result, created_at) VALUES (?, ?, ?, ?, NOW() - INTERVAL '25 months')",
                heldOldAuditA, businessA, "HELD_OLD_A", "SUCCESS");
        ownerJdbc.update(
                "INSERT INTO retention_legal_hold(business_id, target_type, target_id, reason_code, actor_type) VALUES (?, 'AUDIT_LOG', ?, 'LEGAL_REQUEST', 'SYSTEM')",
                businessA, heldOldAuditA);
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
                heldOldAuditA));
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
    void legalHoldFoundationIsTenantIsolatedReadOnlyAndUniquePerActiveTarget() {
        UUID targetA = UUID.randomUUID();
        UUID targetB = UUID.randomUUID();
        UUID actorA = ownerJdbc.queryForObject(
                "SELECT id FROM app_user WHERE business_id = ? LIMIT 1",
                UUID.class,
                businessA);
        UUID actorB = ownerJdbc.queryForObject(
                "SELECT id FROM app_user WHERE business_id = ? LIMIT 1",
                UUID.class,
                businessB);

        ownerJdbc.update("""
                INSERT INTO retention_legal_hold(
                    business_id, target_type, target_id, reason_code, actor_type, actor_user_id
                ) VALUES (?, 'CUSTOMER', ?, 'LEGAL_REQUEST', 'HUMAN', ?)
                """, businessA, targetA, actorA);
        ownerJdbc.update("""
                INSERT INTO retention_legal_hold(
                    business_id, target_type, target_id, reason_code, actor_type, actor_user_id
                ) VALUES (?, 'CUSTOMER', ?, 'PAYMENT_DISPUTE', 'HUMAN', ?)
                """, businessB, targetB, actorB);

        long visibleA = databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM retention_legal_hold",
                        Long.class));
        long visibleB = databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM retention_legal_hold",
                        Long.class));

        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update("""
                        INSERT INTO retention_legal_hold(
                            business_id, target_type, target_id, reason_code, actor_type, actor_user_id
                        ) VALUES (?, 'CUSTOMER', ?, 'LEGAL_REQUEST', 'HUMAN', ?)
                        """, businessA, UUID.randomUUID(), actorA)));

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                INSERT INTO retention_legal_hold(
                    business_id, target_type, target_id, reason_code, actor_type, actor_user_id
                ) VALUES (?, 'CUSTOMER', ?, 'CONTRACTUAL', 'HUMAN', ?)
                """, businessA, targetA, actorA));

        ownerJdbc.update("""
                UPDATE retention_legal_hold
                   SET released_at = NOW()
                 WHERE business_id = ?
                   AND target_type = 'CUSTOMER'
                   AND target_id = ?
                """, businessA, targetA);

        ownerJdbc.update("""
                INSERT INTO retention_legal_hold(
                    business_id, target_type, target_id, reason_code, actor_type, actor_user_id
                ) VALUES (?, 'CUSTOMER', ?, 'CONTRACTUAL', 'HUMAN', ?)
                """, businessA, targetA, actorA);

        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.retention_legal_hold', 'INSERT')",
                Boolean.class));
        assertFalse(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.retention_legal_hold', 'UPDATE')",
                Boolean.class));
        assertTrue(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.retention_legal_hold', 'SELECT')",
                Boolean.class));
    }

    @Test
    void inventoryAlertsAreTenantIsolatedAndCrossTenantWritesFail() {
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        UUID alertA = UUID.randomUUID();
        UUID alertB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemA, businessA, "Inventory A");
        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemB, businessB, "Inventory B");

        ownerJdbc.update("""
                INSERT INTO inventory_alert(
                    id, business_id, catalog_item_id, alert_type, status,
                    subject_name, available, reorder_threshold
                ) VALUES (?, ?, ?, 'LOW_STOCK', 'OPEN', ?, 1, 2)
                """, alertA, businessA, itemA, "Inventory A");
        ownerJdbc.update("""
                INSERT INTO inventory_alert(
                    id, business_id, catalog_item_id, alert_type, status,
                    subject_name, available, reorder_threshold
                ) VALUES (?, ?, ?, 'OUT_OF_STOCK', 'OPEN', ?, 0, 2)
                """, alertB, businessB, itemB, "Inventory B");

        long visibleA = databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_alert",
                        Long.class));
        long visibleB = databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_alert",
                        Long.class));

        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update("""
                        INSERT INTO inventory_alert(
                            id, business_id, catalog_item_id, alert_type, status,
                            subject_name, available, reorder_threshold
                        ) VALUES (?, ?, ?, 'LOW_STOCK', 'OPEN', ?, 1, 2)
                        """, UUID.randomUUID(), businessB, itemB, "Cross tenant")));

        assertEquals(1L, databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_alert",
                        Long.class)));
    }

    @Test
    void inventoryForeignKeysRejectCrossTenantCatalogAndVariantIdentityEvenForOwner() {
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        UUID variantB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemA, businessA, "Integrity A");
        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemB, businessB, "Integrity B");

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                INSERT INTO inventory_stock(
                    business_id, catalog_item_id, sku, tracking_enabled,
                    on_hand, reserved, reorder_threshold
                ) VALUES (?, ?, ?, TRUE, 1, 0, 0)
                """, businessA, itemB, "CROSS-" + UUID.randomUUID()));

        ownerJdbc.update("""
                INSERT INTO inventory_product_variant(
                    id, business_id, catalog_item_id, name, sku,
                    tracking_enabled, on_hand, reserved, reorder_threshold, active
                ) VALUES (?, ?, ?, 'Variant B', ?, TRUE, 0, 0, 0, TRUE)
                """, variantB, businessB, itemB, "VAR-" + UUID.randomUUID());

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                INSERT INTO inventory_alert(
                    business_id, catalog_item_id, variant_id, alert_type, status,
                    subject_name, available, reorder_threshold
                ) VALUES (?, ?, ?, 'OUT_OF_STOCK', 'OPEN', 'Cross tenant variant', 0, 0)
                """, businessA, itemA, variantB));
    }

    @Test
    void inventoryRestockSubscriptionsAndNotificationsAreTenantIsolated() {
        UUID customerA = ownerJdbc.queryForObject(
                "SELECT id FROM customer WHERE business_id = ? LIMIT 1",
                UUID.class,
                businessA);
        UUID customerB = ownerJdbc.queryForObject(
                "SELECT id FROM customer WHERE business_id = ? LIMIT 1",
                UUID.class,
                businessB);
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        UUID subscriptionA = UUID.randomUUID();
        UUID subscriptionB = UUID.randomUUID();

        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemA, businessA, "Restock A");
        ownerJdbc.update(
                "INSERT INTO catalog_item(id, business_id, kind, name) VALUES (?, ?, 'PRODUCT', ?)",
                itemB, businessB, "Restock B");

        ownerJdbc.update("""
                INSERT INTO inventory_restock_subscription(
                    id, business_id, customer_id, catalog_item_id,
                    preferred_channel, contact, normalized_contact,
                    consent_granted, consent_granted_at, consent_source, status
                ) VALUES (?, ?, ?, ?, 'WHATSAPP', ?, ?, TRUE, NOW(), 'VOICE', 'ACTIVE')
                """, subscriptionA, businessA, customerA, itemA,
                "+56911111111", "+56911111111");
        ownerJdbc.update("""
                INSERT INTO inventory_restock_subscription(
                    id, business_id, customer_id, catalog_item_id,
                    preferred_channel, contact, normalized_contact,
                    consent_granted, consent_granted_at, consent_source, status
                ) VALUES (?, ?, ?, ?, 'WHATSAPP', ?, ?, TRUE, NOW(), 'VOICE', 'ACTIVE')
                """, subscriptionB, businessB, customerB, itemB,
                "+56922222222", "+56922222222");

        ownerJdbc.update("""
                INSERT INTO inventory_restock_notification(
                    business_id, subscription_id, customer_id, catalog_item_id,
                    preferred_channel, contact, subject_name, available,
                    status, idempotency_key
                ) VALUES (?, ?, ?, ?, 'WHATSAPP', ?, ?, 4, 'PENDING', ?)
                """, businessA, subscriptionA, customerA, itemA,
                "+56911111111", "Restock A", "inventory-restock:" + subscriptionA);
        ownerJdbc.update("""
                INSERT INTO inventory_restock_notification(
                    business_id, subscription_id, customer_id, catalog_item_id,
                    preferred_channel, contact, subject_name, available,
                    status, idempotency_key
                ) VALUES (?, ?, ?, ?, 'WHATSAPP', ?, ?, 5, 'PENDING', ?)
                """, businessB, subscriptionB, customerB, itemB,
                "+56922222222", "Restock B", "inventory-restock:" + subscriptionB);

        assertEquals(1L, databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_restock_subscription",
                        Long.class)));
        assertEquals(1L, databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_restock_subscription",
                        Long.class)));
        assertEquals(1L, databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_restock_notification",
                        Long.class)));
        assertEquals(1L, databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_restock_notification",
                        Long.class)));

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update("""
                        INSERT INTO inventory_restock_subscription(
                            business_id, customer_id, catalog_item_id,
                            preferred_channel, contact, normalized_contact,
                            consent_granted, consent_granted_at, consent_source, status
                        ) VALUES (?, ?, ?, 'WHATSAPP', ?, ?, TRUE, NOW(), 'VOICE', 'ACTIVE')
                        """, businessB, customerB, itemB,
                        "+56929999999", "+56929999999")));

        assertEquals(1L, databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_restock_subscription",
                        Long.class)));
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

    @Test
    void liveDemoFoundationKeepsLifecycleValidAndProfilesPlatformOnly() {
        assertEquals(
                "CUSTOMER",
                ownerJdbc.queryForObject(
                        "SELECT mode FROM business WHERE id = ?",
                        String.class,
                        businessA));

        assertThrows(DataAccessException.class, () ->
                ownerJdbc.update("UPDATE business SET mode = 'INVALID' WHERE id = ?", businessA));

        for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
            assertFalse(ownerJdbc.queryForObject(
                    "SELECT has_table_privilege('helvoca_runtime', 'public.demo_profile', ?)",
                    Boolean.class,
                    privilege));
            assertTrue(ownerJdbc.queryForObject(
                    "SELECT has_table_privilege('helvoca_system', 'public.demo_profile', ?)",
                    Boolean.class,
                    privilege));
        }
    }

    @Test
    void demoSessionsAreForceRlsIsolatedAndRuntimeReadOnly() {
        UUID profileId = UUID.randomUUID();
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();

        ownerJdbc.update("""
                INSERT INTO demo_profile(id, display_name, business_name, greeting)
                VALUES (?, 'RLS demo', 'RLS demo', 'Hola')
                """, profileId);
        ownerJdbc.update("""
                INSERT INTO demo_session(
                    id, correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                ) VALUES (?, ?, ?, ?, 'FINISHED', 'rev-a')
                """, sessionA, UUID.randomUUID(), profileId, businessA);
        ownerJdbc.update("""
                INSERT INTO demo_session(
                    id, correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                ) VALUES (?, ?, ?, ?, 'FINISHED', 'rev-b')
                """, sessionB, UUID.randomUUID(), profileId, businessB);

        long visibleA = databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM demo_session", Long.class));
        long visibleB = databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject("SELECT COUNT(*) FROM demo_session", Long.class));
        assertEquals(1L, visibleA);
        assertEquals(1L, visibleB);

        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update("""
                        INSERT INTO demo_session(
                            correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                        ) VALUES (?, ?, ?, 'FINISHED', 'tenant-write')
                        """, UUID.randomUUID(), profileId, businessA)));
        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update(
                        "UPDATE demo_session SET failure_reason = 'mutated' WHERE id = ?",
                        sessionA)));
        assertThrows(DataAccessException.class, () -> databaseContext.runAsTenant(
                businessA,
                () -> runtimeJdbc.update("DELETE FROM demo_session WHERE id = ?", sessionA)));

        assertTrue(ownerJdbc.queryForObject("""
                SELECT c.relrowsecurity
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relname = 'demo_session'
                """, Boolean.class));
        assertTrue(ownerJdbc.queryForObject("""
                SELECT c.relforcerowsecurity
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relname = 'demo_session'
                """, Boolean.class));

        assertTrue(ownerJdbc.queryForObject(
                "SELECT has_table_privilege('helvoca_runtime', 'public.demo_session', 'SELECT')",
                Boolean.class));
        for (String privilege : List.of("INSERT", "UPDATE", "DELETE")) {
            assertFalse(ownerJdbc.queryForObject(
                    "SELECT has_table_privilege('helvoca_runtime', 'public.demo_session', ?)",
                    Boolean.class,
                    privilege));
        }
        for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
            assertTrue(ownerJdbc.queryForObject(
                    "SELECT has_table_privilege('helvoca_system', 'public.demo_session', ?)",
                    Boolean.class,
                    privilege));
        }
    }

    @Test
    void demoSessionsEnforceOnePreparedOrActiveSessionPerRuntimeAtDatabaseBoundary() {
        UUID profileId = UUID.randomUUID();

        ownerJdbc.update("""
                INSERT INTO demo_profile(id, display_name, business_name, greeting)
                VALUES (?, 'Unique demo', 'Unique demo', 'Hola')
                """, profileId);
        ownerJdbc.update("""
                INSERT INTO demo_session(
                    correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                ) VALUES (?, ?, ?, 'PREPARING', 'rev-1')
                """, UUID.randomUUID(), profileId, businessA);

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                INSERT INTO demo_session(
                    correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                ) VALUES (?, ?, ?, 'READY', 'rev-2')
                """, UUID.randomUUID(), profileId, businessA));

        ownerJdbc.update(
                "UPDATE demo_session SET state = 'FINISHED', finished_at = NOW() WHERE runtime_business_id = ?",
                businessA);

        ownerJdbc.update("""
                INSERT INTO demo_session(
                    correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision
                ) VALUES (?, ?, ?, 'ACTIVE', 'rev-3')
                """, UUID.randomUUID(), profileId, businessA);

        assertEquals(2L, ownerJdbc.queryForObject(
                "SELECT COUNT(*) FROM demo_session WHERE runtime_business_id = ?",
                Long.class,
                businessA));
        assertEquals(1L, ownerJdbc.queryForObject(
                "SELECT COUNT(*) FROM demo_session WHERE runtime_business_id = ? AND state IN ('PREPARING', 'READY', 'ACTIVE')",
                Long.class,
                businessA));
    }

    @Test
    void demoCallCorrelationForeignKeyCannotCrossRuntimeTenantBoundary() {
        UUID profileId = UUID.randomUUID();
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        UUID phoneA = UUID.randomUUID();
        UUID phoneB = UUID.randomUUID();
        UUID callA = UUID.randomUUID();

        ownerJdbc.update("""
                INSERT INTO demo_profile(id, display_name, business_name, greeting)
                VALUES (?, 'Voice correlation', 'Voice correlation', 'Hola')
                """, profileId);
        ownerJdbc.update("""
                INSERT INTO demo_session(
                    id, correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision,
                    started_at, finished_at
                ) VALUES (?, ?, ?, ?, 'FINISHED', 'voice-a', NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '1 minute')
                """, sessionA, UUID.randomUUID(), profileId, businessA);
        ownerJdbc.update("""
                INSERT INTO demo_session(
                    id, correlation_id, demo_profile_id, runtime_business_id, state, configuration_revision,
                    started_at, finished_at
                ) VALUES (?, ?, ?, ?, 'FINISHED', 'voice-b', NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '1 minute')
                """, sessionB, UUID.randomUUID(), profileId, businessB);

        ownerJdbc.update("""
                INSERT INTO phone_number(id, business_id, phone_number)
                VALUES (?, ?, ?)
                """, phoneA, businessA, "+1500" + Math.abs(phoneA.hashCode()));
        ownerJdbc.update("""
                INSERT INTO phone_number(id, business_id, phone_number)
                VALUES (?, ?, ?)
                """, phoneB, businessB, "+1600" + Math.abs(phoneB.hashCode()));

        ownerJdbc.update("""
                INSERT INTO call_session(
                    id, business_id, phone_number_id, provider_call_id, destination_number,
                    direction, status, started_at, demo_session_id
                ) VALUES (?, ?, ?, ?, ?, 'INBOUND', 'COMPLETED', NOW(), ?)
                """, callA, businessA, phoneA, "CA-demo-" + callA, "+1500", sessionA);

        assertEquals(sessionA, ownerJdbc.queryForObject(
                "SELECT demo_session_id FROM call_session WHERE id = ?",
                UUID.class,
                callA));

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                INSERT INTO call_session(
                    id, business_id, phone_number_id, provider_call_id, destination_number,
                    direction, status, started_at, demo_session_id
                ) VALUES (?, ?, ?, ?, ?, 'INBOUND', 'COMPLETED', NOW(), ?)
                """, UUID.randomUUID(), businessA, phoneA,
                "CA-cross-" + UUID.randomUUID(), "+1500", sessionB));

        assertThrows(DataAccessException.class, () -> ownerJdbc.update("""
                UPDATE call_session
                   SET demo_session_id = ?
                 WHERE id = ?
                """, sessionB, callA));

        assertEquals(1L, databaseContext.callAsTenant(
                businessA,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM call_session WHERE demo_session_id = ?",
                        Long.class,
                        sessionA)));
        assertEquals(0L, databaseContext.callAsTenant(
                businessB,
                () -> runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM call_session WHERE demo_session_id = ?",
                        Long.class,
                        sessionA)));
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
