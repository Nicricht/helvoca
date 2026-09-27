package cl.helvoca.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class InventoryAdminSurfaceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.outbound.delivery-enabled", () -> "false");
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminWorkspaceAndOperationalInventorySurfacesAreInstalled() {
        assertTrue(new ClassPathResource("static/inventory.html").exists(),
                "Inventory workspace must be shipped as a static application surface");

        assertTrue(context.containsBean("inventoryController"));
        assertTrue(context.containsBean("inventoryVariantController"));
        assertTrue(context.containsBean("inventoryAlertController"));
        assertTrue(context.containsBean("inventoryRestockSubscriptionController"));

        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.inventory_alert')",
                String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.inventory_restock_subscription')",
                String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.inventory_restock_notification')",
                String.class));
    }
}
