package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceBranchCoverageTest {
    private InventoryStockRepository stocks;
    private InventoryReservationRepository reservations;
    private InventoryMovementRepository movements;
    private CatalogItemRepository catalog;
    private TenantProvider tenant;
    private BusinessPaymentRepository payments;
    private InventoryProductVariantRepository variants;
    private InventoryAlertService alerts;
    private InventoryService service;

    private UUID businessId;
    private UUID productId;
    private CatalogItem product;

    @BeforeEach
    void setUp() {
        stocks = mock(InventoryStockRepository.class);
        reservations = mock(InventoryReservationRepository.class);
        movements = mock(InventoryMovementRepository.class);
        catalog = mock(CatalogItemRepository.class);
        tenant = mock(TenantProvider.class);
        payments = mock(BusinessPaymentRepository.class);
        variants = mock(InventoryProductVariantRepository.class);
        alerts = mock(InventoryAlertService.class);

        service = new InventoryService(stocks, reservations, movements, catalog, tenant, payments);
        ReflectionTestUtils.setField(service, "variants", variants);
        ReflectionTestUtils.setField(service, "inventoryAlerts", alerts);

        businessId = UUID.randomUUID();
        productId = UUID.randomUUID();
        product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Producto");
        product.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(stocks.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variants.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservations.saveAndFlush(any())).thenAnswer(inv -> {
            InventoryReservation value = inv.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        when(movements.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void configureCoversValidationDuplicateExistingAndUntrackedBranches() {
        assertThrows(IllegalArgumentException.class, () -> service.configure(productId, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.configure(productId, new InventoryService.ConfigureInput("bad sku", true, 1, 0, null)));
        assertThrows(IllegalArgumentException.class, () ->
                service.configure(productId, new InventoryService.ConfigureInput(null, true, -1, 0, null)));
        assertThrows(IllegalArgumentException.class, () ->
                service.configure(productId, new InventoryService.ConfigureInput(null, true, 1, -1, null)));

        InventoryStock other = stock(UUID.randomUUID(), 1, 0, true);
        when(stocks.findByBusinessIdAndSkuIgnoreCase(businessId, "DUP-1"))
                .thenReturn(Optional.of(other));
        assertThrows(ConflictException.class, () ->
                service.configure(productId, new InventoryService.ConfigureInput("dup-1", true, 2, 0, null)));

        InventoryStock existing = stock(productId, 5, 2, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(existing));
        assertThrows(ConflictException.class, () ->
                service.configure(productId, new InventoryService.ConfigureInput(null, true, 1, 0, null)));

        existing.setReserved(0);
        InventoryService.StockView view = service.configure(
                productId, new InventoryService.ConfigureInput(" ", false, null, null, " "));
        assertFalse(view.trackingEnabled());
        assertEquals(0, view.onHand());
        assertEquals(0, view.reorderThreshold());
        assertFalse(view.lowStock());
        verify(alerts).evaluateBase(existing);
    }

    @Test
    void adjustCoversZeroDisabledOverflowAndSuccessfulNormalization() {
        assertThrows(IllegalArgumentException.class, () -> service.adjust(productId, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.adjust(productId, new InventoryService.AdjustmentInput(0, null, null, null)));

        InventoryStock disabled = stock(productId, 4, 0, false);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(disabled));
        assertThrows(ConflictException.class, () ->
                service.adjust(productId, new InventoryService.AdjustmentInput(1, null, null, null)));

        InventoryStock maxed = stock(productId, Integer.MAX_VALUE, 0, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(maxed));
        assertThrows(IllegalArgumentException.class, () ->
                service.adjust(productId, new InventoryService.AdjustmentInput(1, null, null, null)));

        InventoryStock normal = stock(productId, 5, 0, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(normal));
        InventoryService.StockView view = service.adjust(
                productId, new InventoryService.AdjustmentInput(2, " manual_fix ", UUID.randomUUID(), " note "));
        assertEquals(7, view.onHand());
        verify(movements, atLeastOnce()).save(any());
    }

    @Test
    void reserveReleaseAndConsumeCoverStatusAndConsistencyBranches() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(productId, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.reserve(productId, new InventoryService.ReservationInput(0, null, null, null, null)));

        InventoryStock stock = stock(productId, 5, 0, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));
        assertThrows(IllegalArgumentException.class, () ->
                service.reserve(productId, new InventoryService.ReservationInput(
                        1, null, null, Instant.now().minusSeconds(60), null)));

        UUID releasedId = UUID.randomUUID();
        InventoryReservation released = reservation(releasedId, 1, InventoryReservation.Status.RELEASED);
        when(reservations.lockByIdAndBusinessId(releasedId, businessId)).thenReturn(Optional.of(released));
        InventoryStock releasedStock = stock(productId, 5, 0, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(releasedStock));
        assertEquals(InventoryReservation.Status.RELEASED, service.release(releasedId, null).status());

        UUID expiredId = UUID.randomUUID();
        InventoryReservation expired = reservation(expiredId, 1, InventoryReservation.Status.EXPIRED);
        when(reservations.lockByIdAndBusinessId(expiredId, businessId)).thenReturn(Optional.of(expired));
        assertEquals(InventoryReservation.Status.EXPIRED, service.release(expiredId, null).status());

        UUID consumedId = UUID.randomUUID();
        InventoryReservation consumed = reservation(consumedId, 1, InventoryReservation.Status.CONSUMED);
        when(reservations.lockByIdAndBusinessId(consumedId, businessId)).thenReturn(Optional.of(consumed));
        assertThrows(ConflictException.class, () -> service.release(consumedId, null));

        UUID invalidConsumeId = UUID.randomUUID();
        InventoryReservation invalidConsume = reservation(invalidConsumeId, 1, InventoryReservation.Status.RELEASED);
        when(reservations.lockByIdAndBusinessId(invalidConsumeId, businessId)).thenReturn(Optional.of(invalidConsume));
        assertThrows(ConflictException.class, () -> service.consume(invalidConsumeId, null));

        UUID inconsistentReservedId = UUID.randomUUID();
        InventoryReservation inconsistentReserved = reservation(
                inconsistentReservedId, 2, InventoryReservation.Status.ACTIVE);
        when(reservations.lockByIdAndBusinessId(inconsistentReservedId, businessId))
                .thenReturn(Optional.of(inconsistentReserved));
        InventoryStock noReserved = stock(productId, 5, 1, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(noReserved));
        assertThrows(ConflictException.class, () -> service.consume(inconsistentReservedId, null));

        UUID inconsistentOnHandId = UUID.randomUUID();
        InventoryReservation inconsistentOnHand = reservation(
                inconsistentOnHandId, 2, InventoryReservation.Status.ACTIVE);
        when(reservations.lockByIdAndBusinessId(inconsistentOnHandId, businessId))
                .thenReturn(Optional.of(inconsistentOnHand));
        InventoryStock noOnHand = stock(productId, 1, 2, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(noOnHand));
        assertThrows(ConflictException.class, () -> service.consume(inconsistentOnHandId, null));
    }

    @Test
    void lookupCoversVariantBaseSkuAndMissingBranches() {
        assertThrows(IllegalArgumentException.class, () ->
                service.lookupForBusiness(null, productId, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.lookupForBusiness(businessId, null, null, null));

        UUID variantId = UUID.randomUUID();
        InventoryProductVariant inactive = variant(variantId, productId, false, false, 0, 0, 0);
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(inactive));
        assertThrows(NotFoundException.class, () ->
                service.lookupForBusiness(businessId, productId, variantId, null));

        UUID otherProduct = UUID.randomUUID();
        InventoryProductVariant mismatched = variant(variantId, otherProduct, true, true, 2, 0, 1);
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(mismatched));
        assertThrows(ConflictException.class, () ->
                service.lookupForBusiness(businessId, productId, variantId, null));

        InventoryProductVariant untracked = variant(variantId, productId, true, false, 7, 2, 3);
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(untracked));
        InventoryService.StockLookupView variantView =
                service.lookupForBusiness(businessId, productId, variantId, null);
        assertTrue(variantView.configured());
        assertFalse(variantView.trackingEnabled());
        assertNull(variantView.onHand());
        assertNull(variantView.available());
        assertFalse(variantView.lowStock());

        InventoryStock base = stock(productId, 5, 1, false);
        base.setSku("BASE-1");
        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId)).thenReturn(Optional.of(base));
        InventoryService.StockLookupView baseView =
                service.lookupForBusiness(businessId, productId, null, null);
        assertTrue(baseView.configured());
        assertFalse(baseView.trackingEnabled());
        assertNull(baseView.available());

        InventoryStock skuStock = stock(productId, 5, 1, true);
        skuStock.setSku("BASE-SKU");
        when(stocks.findByBusinessIdAndSkuIgnoreCase(businessId, "BASE-SKU")).thenReturn(Optional.of(skuStock));
        InventoryService.StockLookupView skuView =
                service.lookupForBusiness(businessId, null, null, " base-sku ");
        assertEquals(4, skuView.available());

        when(stocks.findByBusinessIdAndSkuIgnoreCase(businessId, "UNKNOWN")).thenReturn(Optional.empty());
        when(variants.findByBusinessIdAndSkuIgnoreCase(businessId, "UNKNOWN")).thenReturn(Optional.empty());
        InventoryService.StockLookupView unknown =
                service.lookupForBusiness(businessId, null, null, "unknown");
        assertFalse(unknown.configured());
        assertNull(unknown.catalogItemId());

        ReflectionTestUtils.setField(service, "variants", null);
        assertThrows(IllegalArgumentException.class, () ->
                service.lookupForBusiness(businessId, productId, variantId, null));
    }

    @Test
    void reserveOrderCoversNoopExistingInvalidUntrackedAndVariantFailureBranches() {
        UUID orderId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                service.reserveOrder(null, orderId, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                service.reserveOrder(businessId, null, List.of()));

        assertTrue(service.reserveOrder(businessId, orderId, null).success());
        assertTrue(service.reserveOrder(businessId, orderId, List.of()).success());

        InventoryReservation existing = reservation(UUID.randomUUID(), 1, InventoryReservation.Status.ACTIVE);
        when(reservations.lockAllByBusinessAndReferenceAndStatus(
                businessId, "ORDER_OPERATION", orderId, InventoryReservation.Status.ACTIVE))
                .thenReturn(List.of(existing));
        InventoryService.OrderReservationResult replay = service.reserveOrder(
                businessId, orderId, List.of(new InventoryService.OrderItem(productId, 1)));
        assertEquals(List.of(existing.getId()), replay.reservationIds());

        UUID freshOrder = UUID.randomUUID();
        when(reservations.lockAllByBusinessAndReferenceAndStatus(
                businessId, "ORDER_OPERATION", freshOrder, InventoryReservation.Status.ACTIVE))
                .thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () ->
                service.reserveOrder(businessId, freshOrder, List.of((InventoryService.OrderItem) null)));
        assertThrows(IllegalArgumentException.class, () ->
                service.reserveOrder(businessId, freshOrder,
                        List.of(new InventoryService.OrderItem(null, 1))));
        assertThrows(IllegalArgumentException.class, () ->
                service.reserveOrder(businessId, freshOrder,
                        List.of(new InventoryService.OrderItem(productId, 0))));

        UUID variantId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "variants", null);
        InventoryService.OrderReservationResult unavailable = service.reserveOrder(
                businessId, freshOrder,
                List.of(new InventoryService.OrderItem(productId, variantId, 1)));
        assertEquals("VARIANT_INVENTORY_UNAVAILABLE", unavailable.code());

        ReflectionTestUtils.setField(service, "variants", variants);
        when(variants.lockByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.empty());
        InventoryService.OrderReservationResult missing = service.reserveOrder(
                businessId, freshOrder,
                List.of(new InventoryService.OrderItem(productId, variantId, 1)));
        assertEquals("VARIANT_NOT_FOUND", missing.code());

        InventoryProductVariant untracked = variant(variantId, productId, true, false, 0, 0, 0);
        when(variants.lockByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(untracked));
        InventoryService.OrderReservationResult untrackedResult = service.reserveOrder(
                businessId, freshOrder,
                List.of(new InventoryService.OrderItem(productId, variantId, 2)));
        assertTrue(untrackedResult.success());
        assertTrue(untrackedResult.reservationIds().isEmpty());

        InventoryProductVariant scarce = variant(variantId, productId, true, true, 1, 0, 0);
        scarce.setName("Escasa");
        when(variants.lockByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(scarce));
        InventoryService.OrderReservationResult insufficient = service.reserveOrder(
                businessId, freshOrder,
                List.of(new InventoryService.OrderItem(productId, variantId, 2)));
        assertEquals("INSUFFICIENT_STOCK", insufficient.code());

        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.empty());
        InventoryService.OrderReservationResult unconfigured = service.reserveOrder(
                businessId, freshOrder, List.of(new InventoryService.OrderItem(productId, 1)));
        assertTrue(unconfigured.success());
        assertTrue(unconfigured.reservationIds().isEmpty());
    }

    @Test
    void expiryCoversNoopPaymentUnavailableAndFailedPaymentReleaseBranches() {
        UUID reservationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-27T05:00:00Z");

        assertEquals(InventoryService.ExpiryResult.NOOP,
                service.expireReservationForBusiness(null, reservationId, now));
        assertEquals(InventoryService.ExpiryResult.NOOP,
                service.expireReservationForBusiness(businessId, null, now));

        when(reservations.lockByIdAndBusinessId(reservationId, businessId)).thenReturn(Optional.empty());
        assertEquals(InventoryService.ExpiryResult.NOOP,
                service.expireReservationForBusiness(businessId, reservationId, now));

        InventoryReservation future = reservation(reservationId, 1, InventoryReservation.Status.ACTIVE);
        future.setExpiresAt(now.plusSeconds(60));
        when(reservations.lockByIdAndBusinessId(reservationId, businessId)).thenReturn(Optional.of(future));
        assertEquals(InventoryService.ExpiryResult.NOOP,
                service.expireReservationForBusiness(businessId, reservationId, now));

        UUID orderId = UUID.randomUUID();
        InventoryReservation expiredOrder = reservation(reservationId, 1, InventoryReservation.Status.ACTIVE);
        expiredOrder.setExpiresAt(now.minusSeconds(1));
        expiredOrder.setReferenceType("ORDER_OPERATION");
        expiredOrder.setReferenceId(orderId);
        when(reservations.lockByIdAndBusinessId(reservationId, businessId)).thenReturn(Optional.of(expiredOrder));

        ReflectionTestUtils.setField(service, "payments", null);
        assertEquals(InventoryService.ExpiryResult.PAYMENT_STATE_UNAVAILABLE,
                service.expireReservationForBusiness(businessId, reservationId, now));

        ReflectionTestUtils.setField(service, "payments", payments);
        BusinessPayment failed = new BusinessPayment();
        failed.setStatus(BusinessPayment.Status.FAILED);
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(businessId, orderId))
                .thenReturn(List.of(failed));
        InventoryStock stock = stock(productId, 5, 1, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));

        assertEquals(InventoryService.ExpiryResult.EXPIRED,
                service.expireReservationForBusiness(businessId, reservationId, null));
        assertEquals(InventoryReservation.Status.EXPIRED, expiredOrder.getStatus());
    }

    @Test
    void releaseOrderCoversBaseAndVariantReleasePathsAndSkippedCandidates() {
        UUID orderId = UUID.randomUUID();
        service.releaseOrder(null, orderId, null);
        service.releaseOrder(businessId, null, null);

        InventoryReservation missing = reservation(UUID.randomUUID(), 1, InventoryReservation.Status.ACTIVE);
        InventoryReservation stale = reservation(UUID.randomUUID(), 1, InventoryReservation.Status.ACTIVE);
        InventoryReservation baseReservation = reservation(UUID.randomUUID(), 2, InventoryReservation.Status.ACTIVE);
        InventoryReservation variantReservation = reservation(UUID.randomUUID(), 1, InventoryReservation.Status.ACTIVE);
        UUID variantId = UUID.randomUUID();
        variantReservation.setVariantId(variantId);

        when(reservations.findAllByBusinessIdAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtAsc(
                businessId, "ORDER_OPERATION", orderId, InventoryReservation.Status.ACTIVE))
                .thenReturn(List.of(missing, stale, baseReservation, variantReservation));
        when(reservations.lockByIdAndBusinessId(missing.getId(), businessId)).thenReturn(Optional.empty());

        InventoryReservation noLongerActive = reservation(stale.getId(), 1, InventoryReservation.Status.RELEASED);
        when(reservations.lockByIdAndBusinessId(stale.getId(), businessId))
                .thenReturn(Optional.of(noLongerActive));
        when(reservations.lockByIdAndBusinessId(baseReservation.getId(), businessId))
                .thenReturn(Optional.of(baseReservation));
        when(reservations.lockByIdAndBusinessId(variantReservation.getId(), businessId))
                .thenReturn(Optional.of(variantReservation));

        InventoryStock base = stock(productId, 5, 2, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(base));

        InventoryProductVariant variant = variant(variantId, productId, true, true, 3, 1, 0);
        when(variants.lockByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(variant));

        service.releaseOrder(businessId, orderId, "cancelled");

        assertEquals(0, base.getReserved());
        assertEquals(InventoryReservation.Status.RELEASED, baseReservation.getStatus());
        assertEquals(0, variant.getReserved());
        assertEquals(InventoryReservation.Status.RELEASED, variantReservation.getStatus());
    }

    private InventoryStock stock(UUID catalogItemId, int onHand, int reserved, boolean tracking) {
        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(businessId);
        stock.setCatalogItemId(catalogItemId);
        stock.setTrackingEnabled(tracking);
        stock.setOnHand(onHand);
        stock.setReserved(reserved);
        stock.setReorderThreshold(1);
        return stock;
    }

    private InventoryReservation reservation(UUID id, int quantity, InventoryReservation.Status status) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(id);
        reservation.setBusinessId(businessId);
        reservation.setCatalogItemId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        reservation.setExpiresAt(Instant.parse("2026-09-27T04:00:00Z"));
        return reservation;
    }

    private InventoryProductVariant variant(UUID id,
                                            UUID catalogItemId,
                                            boolean active,
                                            boolean tracking,
                                            int onHand,
                                            int reserved,
                                            int threshold) {
        InventoryProductVariant variant = new InventoryProductVariant();
        variant.setId(id);
        variant.setBusinessId(businessId);
        variant.setCatalogItemId(catalogItemId);
        variant.setName("Variante");
        variant.setSku("VAR-1");
        variant.setActive(active);
        variant.setTrackingEnabled(tracking);
        variant.setOnHand(onHand);
        variant.setReserved(reserved);
        variant.setReorderThreshold(threshold);
        return variant;
    }
}
