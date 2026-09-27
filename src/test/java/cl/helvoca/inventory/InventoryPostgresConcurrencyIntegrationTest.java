package cl.helvoca.inventory;

import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.PaymentProviderAdapter;
import cl.helvoca.payment.PaymentWebhookService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Import(InventoryPostgresConcurrencyIntegrationTest.TestPaymentProviderConfig.class)
class InventoryPostgresConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "8");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired InventoryService inventory;
    @Autowired PaymentWebhookService paymentWebhooks;
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;

    JdbcTemplate ownerJdbc;
    UUID businessA;
    UUID businessB;

    @BeforeEach
    void setUp() {
        ownerJdbc = new JdbcTemplate(migrationDataSource);
        businessA = UUID.randomUUID();
        businessB = UUID.randomUUID();
        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessA, "Concurrency A " + businessA);
        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessB, "Concurrency B " + businessB);
    }

    @Test
    void twoSimultaneousPurchasesCannotSellMoreThanPhysicalStock() throws Exception {
        UUID productId = createBaseStock(businessA, 5, "BASE-5");
        UUID orderOne = UUID.randomUUID();
        UUID orderTwo = UUID.randomUUID();

        List<Attempt> attempts = race(List.of(
                () -> reserve(businessA, orderOne, productId, null, 3),
                () -> reserve(businessA, orderTwo, productId, null, 3)
        ));

        List<Attempt> successful = attempts.stream()
                .filter(value -> value.result().success())
                .toList();
        assertEquals(1, successful.size());
        assertEquals(1, attempts.stream().filter(value -> !value.result().success()).count());
        assertEquals("INSUFFICIENT_STOCK",
                attempts.stream()
                        .filter(value -> !value.result().success())
                        .findFirst()
                        .orElseThrow()
                        .result()
                        .code());

        assertStock(productId, 5, 3);
        assertEquals(3, activeReservedQuantity(businessA, productId, null));

        UUID winningOrder = successful.get(0).orderId();
        databaseContext.runAsTenant(
                businessA,
                () -> inventory.consumeOrder(businessA, winningOrder, "concurrency purchase"));

        assertStock(productId, 2, 0);
        assertEquals(1, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND reference_id = ?
                   AND status = 'CONSUMED'
                """, Integer.class, businessA, winningOrder));
        assertEquals(0, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_stock
                 WHERE business_id = ?
                   AND (on_hand < 0 OR reserved < 0 OR reserved > on_hand)
                """, Integer.class, businessA));
    }

    @Test
    void concurrentReservationsNeverMakeReservedExceedOnHand() throws Exception {
        UUID productId = createBaseStock(businessA, 3, "BASE-3");
        List<Callable<Attempt>> work = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID orderId = UUID.randomUUID();
            work.add(() -> reserve(businessA, orderId, productId, null, 1));
        }

        List<Attempt> attempts = race(work);

        assertEquals(3, attempts.stream().filter(value -> value.result().success()).count());
        assertEquals(3, attempts.stream().filter(value -> !value.result().success()).count());
        assertStock(productId, 3, 3);
        assertEquals(3, activeReservedQuantity(businessA, productId, null));
        assertEquals(0, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_stock
                 WHERE business_id = ?
                   AND reserved > on_hand
                """, Integer.class, businessA));
    }

    @Test
    void baseProductAndVariantsKeepIndependentConcurrentStockIdentities() throws Exception {
        UUID productId = createBaseStock(businessA, 2, "BASE-INDEPENDENT");
        UUID variantOne = createVariant(businessA, productId, 3, "VARIANT-ONE", "Negro / 42");
        UUID variantTwo = createVariant(businessA, productId, 4, "VARIANT-TWO", "Azul / 43");

        UUID baseOrder = UUID.randomUUID();
        UUID firstVariantOrder = UUID.randomUUID();
        UUID secondVariantOrder = UUID.randomUUID();

        List<Attempt> attempts = race(List.of(
                () -> reserve(businessA, baseOrder, productId, null, 2),
                () -> reserve(businessA, firstVariantOrder, productId, variantOne, 3),
                () -> reserve(businessA, secondVariantOrder, productId, variantTwo, 4)
        ));

        assertTrue(attempts.stream().allMatch(value -> value.result().success()));
        assertStock(productId, 2, 2);
        assertVariant(variantOne, 3, 3);
        assertVariant(variantTwo, 4, 4);

        assertEquals(2, activeReservedQuantity(businessA, productId, null));
        assertEquals(3, activeReservedQuantity(businessA, productId, variantOne));
        assertEquals(4, activeReservedQuantity(businessA, productId, variantTwo));
    }

    @Test
    void tenantContextCannotMutateAnotherTenantsInventoryEvenWithForeignBusinessId() throws Exception {
        UUID productA = createBaseStock(businessA, 4, "TENANT-A");
        UUID productB = createBaseStock(businessB, 4, "TENANT-B");
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID forgedOrder = UUID.randomUUID();

        List<Callable<Attempt>> work = List.of(
                () -> reserve(businessA, orderA, productA, null, 2),
                () -> reserve(businessB, orderB, productB, null, 2),
                () -> databaseContext.callAsTenant(
                        businessA,
                        () -> new Attempt(
                                forgedOrder,
                                inventory.reserveOrder(
                                        businessB,
                                        forgedOrder,
                                        List.of(new InventoryService.OrderItem(productB, 3)))))
        );

        List<Attempt> attempts = race(work);

        Attempt forged = attempts.stream()
                .filter(value -> value.orderId().equals(forgedOrder))
                .findFirst()
                .orElseThrow();
        assertTrue(forged.result().reservationIds().isEmpty());

        assertStockForBusiness(businessA, productA, 4, 2);
        assertStockForBusiness(businessB, productB, 4, 2);
        assertEquals(0, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND reference_id = ?
                """, Integer.class, businessB, forgedOrder));
    }

    @Test
    void paymentWebhookReplayCannotConsumeInventoryTwice() {
        UUID productId = createBaseStock(businessA, 5, "PAYMENT-REPLAY");
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        String externalId = "payment-" + UUID.randomUUID();

        createOperation(orderOperationId, businessA, "ORDER", "CONFIRMED");
        createOperation(paymentOperationId, businessA, "PAYMENT", "CONFIRMED");

        InventoryService.OrderReservationResult held = databaseContext.callAsTenant(
                businessA,
                () -> inventory.reserveOrder(
                        businessA,
                        orderOperationId,
                        List.of(new InventoryService.OrderItem(productId, 2))));
        assertTrue(held.success());
        assertStock(productId, 5, 2);

        ownerJdbc.update("""
                INSERT INTO business_payment(
                    operation_id, business_id, target_operation_id,
                    provider, external_id, idempotency_key,
                    amount, currency, status, source
                ) VALUES (?, ?, ?, 'concurrency-test', ?, ?, 1000, 'CLP', 'PENDING', 'API')
                """,
                paymentOperationId,
                businessA,
                orderOperationId,
                externalId,
                "idem-" + paymentOperationId);

        PaymentWebhookService.Result first = databaseContext.callAsTenant(
                businessA,
                () -> paymentWebhooks.processVerified(
                        businessA,
                        "concurrency-test",
                        "event-1",
                        externalId,
                        paymentOperationId.toString(),
                        "{\"status\":\"paid\"}"));

        assertEquals(PaymentWebhookService.Result.PROCESSED, first);
        assertStock(productId, 3, 0);

        PaymentWebhookService.Result exactReplay = databaseContext.callAsTenant(
                businessA,
                () -> paymentWebhooks.processVerified(
                        businessA,
                        "concurrency-test",
                        "event-1",
                        externalId,
                        paymentOperationId.toString(),
                        "{\"status\":\"paid\"}"));

        assertEquals(PaymentWebhookService.Result.DUPLICATE, exactReplay);
        assertStock(productId, 3, 0);

        PaymentWebhookService.Result semanticReplayWithNewEventId = databaseContext.callAsTenant(
                businessA,
                () -> paymentWebhooks.processVerified(
                        businessA,
                        "concurrency-test",
                        "event-2",
                        externalId,
                        paymentOperationId.toString(),
                        "{\"status\":\"paid-again\"}"));

        assertEquals(PaymentWebhookService.Result.PROCESSED, semanticReplayWithNewEventId);
        assertStock(productId, 3, 0);
        assertEquals(1, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_movement
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND movement_type = 'CONSUMPTION'
                   AND reference_id = ?
                """, Integer.class, businessA, productId, orderOperationId));
        assertEquals(1, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND reference_id = ?
                   AND status = 'CONSUMED'
                """, Integer.class, businessA, productId, orderOperationId));
    }

    @Test
    void twoSequentialPaidPurchasesDepleteStockAndThirdReservationFails() {
        UUID productId = createBaseStock(businessA, 2, "COMMERCIAL-DEPLETION");
        UUID firstOrderId = UUID.randomUUID();
        UUID firstPaymentOperationId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        UUID secondPaymentOperationId = UUID.randomUUID();
        UUID thirdOrderId = UUID.randomUUID();

        createOperation(firstOrderId, businessA, "ORDER", "CONFIRMED");
        createOperation(firstPaymentOperationId, businessA, "PAYMENT", "CONFIRMED");
        createOperation(secondOrderId, businessA, "ORDER", "CONFIRMED");
        createOperation(secondPaymentOperationId, businessA, "PAYMENT", "CONFIRMED");
        createOperation(thirdOrderId, businessA, "ORDER", "CONFIRMED");

        InventoryService.OrderReservationResult firstHold = databaseContext.callAsTenant(
                businessA,
                () -> inventory.reserveOrder(
                        businessA,
                        firstOrderId,
                        List.of(new InventoryService.OrderItem(productId, 1))));
        assertTrue(firstHold.success());
        assertEquals(1, firstHold.reservationIds().size());
        assertStock(productId, 2, 1);

        String firstExternalId = createPendingPayment(
                firstPaymentOperationId, firstOrderId, "commercial-depletion-first");

        assertEquals(PaymentWebhookService.Result.PROCESSED,
                databaseContext.callAsTenant(
                        businessA,
                        () -> paymentWebhooks.processVerified(
                                businessA,
                                "concurrency-test",
                                "event-first-" + UUID.randomUUID(),
                                firstExternalId,
                                firstPaymentOperationId.toString(),
                                "{\"status\":\"paid\"}")));

        assertStock(productId, 1, 0);
        assertEquals(1, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND reference_id = ?
                   AND status = 'CONSUMED'
                """, Integer.class, businessA, productId, firstOrderId));

        InventoryService.OrderReservationResult secondHold = databaseContext.callAsTenant(
                businessA,
                () -> inventory.reserveOrder(
                        businessA,
                        secondOrderId,
                        List.of(new InventoryService.OrderItem(productId, 1))));
        assertTrue(secondHold.success());
        assertEquals(1, secondHold.reservationIds().size());
        assertStock(productId, 1, 1);

        String secondExternalId = createPendingPayment(
                secondPaymentOperationId, secondOrderId, "commercial-depletion-second");

        assertEquals(PaymentWebhookService.Result.PROCESSED,
                databaseContext.callAsTenant(
                        businessA,
                        () -> paymentWebhooks.processVerified(
                                businessA,
                                "concurrency-test",
                                "event-second-" + UUID.randomUUID(),
                                secondExternalId,
                                secondPaymentOperationId.toString(),
                                "{\"status\":\"paid\"}")));

        assertStock(productId, 0, 0);

        InventoryService.OrderReservationResult soldOutAttempt = databaseContext.callAsTenant(
                businessA,
                () -> inventory.reserveOrder(
                        businessA,
                        thirdOrderId,
                        List.of(new InventoryService.OrderItem(productId, 1))));

        assertFalse(soldOutAttempt.success());
        assertEquals("INSUFFICIENT_STOCK", soldOutAttempt.code());
        assertTrue(soldOutAttempt.reservationIds().isEmpty());
        assertStock(productId, 0, 0);

        assertEquals(2, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND status = 'CONSUMED'
                """, Integer.class, businessA, productId));
        assertEquals(2, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_movement
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND movement_type = 'CONSUMPTION'
                """, Integer.class, businessA, productId));
        assertEquals(0, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM inventory_stock
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND (on_hand < 0 OR reserved < 0 OR reserved > on_hand)
                """, Integer.class, businessA, productId));
    }

    private Attempt reserve(UUID businessId,
                            UUID orderId,
                            UUID productId,
                            UUID variantId,
                            int quantity) {
        return databaseContext.callAsTenant(
                businessId,
                () -> new Attempt(
                        orderId,
                        inventory.reserveOrder(
                                businessId,
                                orderId,
                                List.of(new InventoryService.OrderItem(
                                        productId, variantId, quantity)))));
    }

    private <T> List<T> race(List<Callable<T>> work) throws Exception {
        CountDownLatch ready = new CountDownLatch(work.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> item : work) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test workers did not start together");
                    }
                    return item.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private UUID createBaseStock(UUID businessId, int onHand, String sku) {
        UUID productId = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO catalog_item(id, business_id, kind, name, active)
                VALUES (?, ?, 'PRODUCT', ?, TRUE)
                """, productId, businessId, "Product " + productId);
        ownerJdbc.update("""
                INSERT INTO inventory_stock(
                    business_id, catalog_item_id, sku,
                    tracking_enabled, on_hand, reserved, reorder_threshold
                ) VALUES (?, ?, ?, TRUE, ?, 0, 0)
                """, businessId, productId, sku + "-" + productId.toString().substring(0, 8), onHand);
        return productId;
    }

    private UUID createVariant(UUID businessId,
                               UUID productId,
                               int onHand,
                               String sku,
                               String name) {
        UUID variantId = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO inventory_product_variant(
                    id, business_id, catalog_item_id, name, option_values_json,
                    sku, tracking_enabled, on_hand, reserved, reorder_threshold, active
                ) VALUES (?, ?, ?, ?, '{}', ?, TRUE, ?, 0, 0, TRUE)
                """,
                variantId,
                businessId,
                productId,
                name,
                sku + "-" + variantId.toString().substring(0, 8),
                onHand);
        return variantId;
    }

    private void createOperation(UUID operationId,
                                 UUID businessId,
                                 String type,
                                 String status) {
        ownerJdbc.update("""
                INSERT INTO business_operation(
                    id, business_id, type, status, source, revision, currency
                ) VALUES (?, ?, ?, ?, 'API', 1, 'CLP')
                """, operationId, businessId, type, status);
    }

    private String createPendingPayment(UUID paymentOperationId,
                                        UUID orderOperationId,
                                        String keyPrefix) {
        String externalId = "payment-" + UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO business_payment(
                    operation_id, business_id, target_operation_id,
                    provider, external_id, idempotency_key,
                    amount, currency, status, source
                ) VALUES (?, ?, ?, 'concurrency-test', ?, ?, 1000, 'CLP', 'PENDING', 'API')
                """,
                paymentOperationId,
                businessA,
                orderOperationId,
                externalId,
                keyPrefix + "-" + paymentOperationId);
        return externalId;
    }

    private int activeReservedQuantity(UUID businessId,
                                       UUID productId,
                                       UUID variantId) {
        if (variantId == null) {
            return ownerJdbc.queryForObject("""
                    SELECT COALESCE(SUM(quantity), 0)
                      FROM inventory_reservation
                     WHERE business_id = ?
                       AND catalog_item_id = ?
                       AND variant_id IS NULL
                       AND status = 'ACTIVE'
                    """, Integer.class, businessId, productId);
        }
        return ownerJdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0)
                  FROM inventory_reservation
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                   AND variant_id = ?
                   AND status = 'ACTIVE'
                """, Integer.class, businessId, productId, variantId);
    }

    private void assertStock(UUID productId, int onHand, int reserved) {
        assertStockForBusiness(businessA, productId, onHand, reserved);
    }

    private void assertStockForBusiness(UUID businessId,
                                        UUID productId,
                                        int onHand,
                                        int reserved) {
        Map<String, Object> row = ownerJdbc.queryForMap("""
                SELECT on_hand, reserved
                  FROM inventory_stock
                 WHERE business_id = ?
                   AND catalog_item_id = ?
                """, businessId, productId);
        assertEquals(onHand, ((Number) row.get("on_hand")).intValue());
        assertEquals(reserved, ((Number) row.get("reserved")).intValue());
        assertTrue(reserved <= onHand);
    }

    private void assertVariant(UUID variantId, int onHand, int reserved) {
        Map<String, Object> row = ownerJdbc.queryForMap("""
                SELECT on_hand, reserved
                  FROM inventory_product_variant
                 WHERE id = ?
                """, variantId);
        assertEquals(onHand, ((Number) row.get("on_hand")).intValue());
        assertEquals(reserved, ((Number) row.get("reserved")).intValue());
        assertTrue(reserved <= onHand);
    }

    record Attempt(UUID orderId, InventoryService.OrderReservationResult result) {}

    @TestConfiguration
    static class TestPaymentProviderConfig {
        @Bean
        PaymentProviderAdapter concurrencyTestPaymentProvider() {
            return new PaymentProviderAdapter() {
                @Override
                public String providerCode() {
                    return "concurrency-test";
                }

                @Override
                public boolean supports(UUID businessId) {
                    return businessId != null;
                }

                @Override
                public CreateResult create(CreateCommand command) {
                    throw new UnsupportedOperationException("Not used by concurrency certification");
                }

                @Override
                public StatusResult getStatus(StatusCommand command) {
                    return new StatusResult(
                            BusinessPayment.Status.SUCCEEDED,
                            Map.of("certification", "inventory-concurrency"));
                }

                @Override
                public CancelResult cancel(CancelCommand command) {
                    throw new UnsupportedOperationException("Not used by concurrency certification");
                }
            };
        }
    }
}
