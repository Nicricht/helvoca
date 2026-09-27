package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryRestockSubscriptionServiceBranchCoverageTest {
    private InventoryRestockSubscriptionRepository subscriptions;
    private InventoryRestockNotificationRepository notifications;
    private CatalogItemRepository catalog;
    private InventoryProductVariantRepository variants;
    private InventoryStockRepository stocks;
    private CustomerRepository customers;
    private TenantProvider tenant;
    private JdbcTemplate jdbc;
    private InventoryRestockSubscriptionService service;

    private UUID businessId;
    private UUID productId;
    private CatalogItem product;
    private InventoryStock emptyTrackedStock;

    @BeforeEach
    void setUp() {
        subscriptions = mock(InventoryRestockSubscriptionRepository.class);
        notifications = mock(InventoryRestockNotificationRepository.class);
        catalog = mock(CatalogItemRepository.class);
        variants = mock(InventoryProductVariantRepository.class);
        stocks = mock(InventoryStockRepository.class);
        customers = mock(CustomerRepository.class);
        tenant = mock(TenantProvider.class);
        jdbc = mock(JdbcTemplate.class);

        service = new InventoryRestockSubscriptionService(
                subscriptions, notifications, catalog, variants, stocks, customers, tenant, jdbc);

        businessId = UUID.randomUUID();
        productId = UUID.randomUUID();

        product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Producto");
        product.setActive(true);

        emptyTrackedStock = new InventoryStock();
        emptyTrackedStock.setBusinessId(businessId);
        emptyTrackedStock.setCatalogItemId(productId);
        emptyTrackedStock.setTrackingEnabled(true);
        emptyTrackedStock.setOnHand(0);
        emptyTrackedStock.setReserved(0);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId))
                .thenReturn(Optional.of(emptyTrackedStock));
        when(subscriptions.saveAndFlush(any())).thenAnswer(inv -> {
            InventoryRestockSubscription value = inv.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        when(notifications.saveAndFlush(any())).thenAnswer(inv -> {
            InventoryRestockNotification value = inv.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test
    void subscribeValidationCoversRequiredProductStockCustomerAndContactBranches() {
        InventoryRestockSubscriptionService.SubscribeRequest valid = emailRequest(null, productId, null, "a@b.cl");

        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(null, valid));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, null));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(
                businessId, new InventoryRestockSubscriptionService.SubscribeRequest(
                        null, productId, null, InventoryRestockSubscription.PreferredChannel.EMAIL,
                        "a@b.cl", false, null)));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(
                businessId, new InventoryRestockSubscriptionService.SubscribeRequest(
                        null, null, null, InventoryRestockSubscription.PreferredChannel.EMAIL,
                        "a@b.cl", true, null)));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(
                businessId, new InventoryRestockSubscriptionService.SubscribeRequest(
                        null, productId, null, null, "a@b.cl", true, null)));

        product.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, valid));
        product.setActive(true);

        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, valid));

        InventoryStock untracked = new InventoryStock();
        untracked.setTrackingEnabled(false);
        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId)).thenReturn(Optional.of(untracked));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, valid));

        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId))
                .thenReturn(Optional.of(emptyTrackedStock));
        UUID customerId = UUID.randomUUID();
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.subscribeForBusiness(
                businessId, emailRequest(customerId, productId, null, "a@b.cl")));

        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(
                businessId, emailRequest(null, productId, null, " ")));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(
                businessId, emailRequest(null, productId, null, "invalid-email")));

        InventoryRestockSubscriptionService.SubscribeRequest badPhone =
                new InventoryRestockSubscriptionService.SubscribeRequest(
                        null, productId, null, InventoryRestockSubscription.PreferredChannel.WHATSAPP,
                        "123", true, "VOICE");
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, badPhone));
    }

    @Test
    void variantSubscriptionCoversMissingMismatchInactiveUntrackedAvailableAndSuccess() {
        UUID variantId = UUID.randomUUID();
        InventoryRestockSubscriptionService.SubscribeRequest request =
                emailRequest(null, productId, variantId, "notify@example.com");

        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.subscribeForBusiness(businessId, request));

        InventoryProductVariant variant = variant(variantId, UUID.randomUUID(), true, true, 0, 0);
        when(variants.findByIdAndBusinessId(variantId, businessId)).thenReturn(Optional.of(variant));
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, request));

        variant.setCatalogItemId(productId);
        variant.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, request));

        variant.setActive(true);
        variant.setTrackingEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, request));

        variant.setTrackingEnabled(true);
        variant.setOnHand(1);
        assertThrows(IllegalArgumentException.class, () -> service.subscribeForBusiness(businessId, request));

        variant.setOnHand(0);
        when(subscriptions.lockActiveDuplicate(
                eq(businessId), eq(productId), eq(variantId),
                eq(InventoryRestockSubscription.PreferredChannel.EMAIL), eq("notify@example.com")))
                .thenReturn(Optional.empty());

        InventoryRestockSubscriptionService.SubscriptionView result =
                service.subscribeForBusiness(businessId, request);
        assertEquals(variantId, result.variantId());
        assertEquals("notify@example.com", result.contact());
        assertEquals("UNKNOWN", result.consentSource());
        assertEquals(InventoryRestockSubscription.Status.ACTIVE, result.status());
    }

    @Test
    void duplicateEmailIsNormalizedAndConsentSourceIsSanitizedAndTruncated() {
        String longSource = " voice source with spaces and symbols !!! 1234567890123456789012345678901234567890 ";
        InventoryRestockSubscription existing = new InventoryRestockSubscription();
        existing.setId(UUID.randomUUID());
        existing.setBusinessId(businessId);
        existing.setCatalogItemId(productId);
        existing.setPreferredChannel(InventoryRestockSubscription.PreferredChannel.EMAIL);
        existing.setContact("Old Display");
        existing.setNormalizedContact("user@example.com");
        existing.setConsentGranted(true);
        existing.setStatus(InventoryRestockSubscription.Status.ACTIVE);

        when(subscriptions.lockActiveDuplicate(
                eq(businessId), eq(productId), isNull(),
                eq(InventoryRestockSubscription.PreferredChannel.EMAIL), eq("user@example.com")))
                .thenReturn(Optional.of(existing));

        InventoryRestockSubscriptionService.SubscriptionView duplicate =
                service.subscribeForBusiness(
                        businessId,
                        new InventoryRestockSubscriptionService.SubscribeRequest(
                                null, productId, null,
                                InventoryRestockSubscription.PreferredChannel.EMAIL,
                                " USER@EXAMPLE.COM ", true, longSource));

        assertEquals(existing.getId(), duplicate.id());
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void listPendingAndCancelCoverDefaultExplicitMissingAndAlreadyCancelledBranches() {
        InventoryRestockSubscription active = new InventoryRestockSubscription();
        active.setId(UUID.randomUUID());
        active.setStatus(InventoryRestockSubscription.Status.ACTIVE);
        when(subscriptions.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
                businessId, InventoryRestockSubscription.Status.ACTIVE)).thenReturn(List.of(active));
        assertEquals(1, service.list(null).size());

        when(subscriptions.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
                businessId, InventoryRestockSubscription.Status.CANCELLED)).thenReturn(List.of());
        assertTrue(service.list(InventoryRestockSubscription.Status.CANCELLED).isEmpty());

        InventoryRestockNotification pending = new InventoryRestockNotification();
        pending.setStatus(InventoryRestockNotification.Status.PENDING);
        when(notifications.findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
                businessId, InventoryRestockNotification.Status.PENDING)).thenReturn(List.of(pending));
        assertEquals(1, service.pendingNotifications().size());

        UUID missingId = UUID.randomUUID();
        when(subscriptions.lockByIdAndBusinessId(missingId, businessId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.cancel(missingId));

        UUID cancelledId = UUID.randomUUID();
        InventoryRestockSubscription cancelled = new InventoryRestockSubscription();
        cancelled.setId(cancelledId);
        cancelled.setStatus(InventoryRestockSubscription.Status.CANCELLED);
        when(subscriptions.lockByIdAndBusinessId(cancelledId, businessId)).thenReturn(Optional.of(cancelled));
        assertEquals(InventoryRestockSubscription.Status.CANCELLED, service.cancel(cancelledId).status());

        UUID activeId = UUID.randomUUID();
        InventoryRestockSubscription cancellable = new InventoryRestockSubscription();
        cancellable.setId(activeId);
        cancellable.setStatus(InventoryRestockSubscription.Status.ACTIVE);
        when(subscriptions.lockByIdAndBusinessId(activeId, businessId)).thenReturn(Optional.of(cancellable));

        InventoryRestockNotification sent = new InventoryRestockNotification();
        sent.setStatus(InventoryRestockNotification.Status.SENT);
        when(notifications.findByBusinessIdAndSubscriptionId(businessId, activeId))
                .thenReturn(Optional.of(sent));

        assertEquals(InventoryRestockSubscription.Status.CANCELLED, service.cancel(activeId).status());
        verify(notifications, never()).saveAndFlush(sent);
    }

    @Test
    void restockCoversInvalidEmptyInactiveExistingAndDefaultNotificationFields() {
        assertEquals(0, service.onRestocked(null, productId, null, null, null, 1));
        assertEquals(0, service.onRestocked(businessId, null, null, null, null, 1));
        assertEquals(0, service.onRestocked(businessId, productId, null, null, null, 0));

        when(subscriptions.lockActiveForSubject(businessId, productId, null)).thenReturn(List.of());
        assertEquals(0, service.onRestocked(businessId, productId, null, null, null, 1));

        InventoryRestockSubscription inactive = new InventoryRestockSubscription();
        inactive.setId(UUID.randomUUID());
        inactive.setStatus(InventoryRestockSubscription.Status.CANCELLED);

        InventoryRestockSubscription existingNotificationSub = subscription("one@example.com");
        InventoryRestockNotification existing = new InventoryRestockNotification();
        existing.setStatus(InventoryRestockNotification.Status.PENDING);
        when(notifications.findByBusinessIdAndSubscriptionId(
                businessId, existingNotificationSub.getId())).thenReturn(Optional.of(existing));

        InventoryRestockSubscription fresh = subscription("two@example.com");
        when(notifications.findByBusinessIdAndSubscriptionId(businessId, fresh.getId()))
                .thenReturn(Optional.empty());

        when(subscriptions.lockActiveForSubject(businessId, productId, null))
                .thenReturn(List.of(inactive, existingNotificationSub, fresh));

        int queued = service.onRestocked(businessId, productId, null, " ", " ", 3);

        assertEquals(1, queued);
        assertEquals(InventoryRestockSubscription.Status.NOTIFIED, existingNotificationSub.getStatus());
        assertEquals(InventoryRestockSubscription.Status.NOTIFIED, fresh.getStatus());

        verify(notifications, times(1)).saveAndFlush(argThat(value ->
                "Producto".equals(value.getSubjectName())
                        && value.getSku() == null
                        && value.getAvailable() == 3
                        && value.getStatus() == InventoryRestockNotification.Status.PENDING));
    }

    private InventoryRestockSubscriptionService.SubscribeRequest emailRequest(
            UUID customerId, UUID catalogItemId, UUID variantId, String contact) {
        return new InventoryRestockSubscriptionService.SubscribeRequest(
                customerId,
                catalogItemId,
                variantId,
                InventoryRestockSubscription.PreferredChannel.EMAIL,
                contact,
                true,
                null);
    }

    private InventoryProductVariant variant(UUID id,
                                            UUID catalogItemId,
                                            boolean active,
                                            boolean tracking,
                                            int onHand,
                                            int reserved) {
        InventoryProductVariant variant = new InventoryProductVariant();
        variant.setId(id);
        variant.setBusinessId(businessId);
        variant.setCatalogItemId(catalogItemId);
        variant.setActive(active);
        variant.setTrackingEnabled(tracking);
        variant.setOnHand(onHand);
        variant.setReserved(reserved);
        return variant;
    }

    private InventoryRestockSubscription subscription(String normalizedContact) {
        InventoryRestockSubscription value = new InventoryRestockSubscription();
        value.setId(UUID.randomUUID());
        value.setBusinessId(businessId);
        value.setCatalogItemId(productId);
        value.setPreferredChannel(InventoryRestockSubscription.PreferredChannel.EMAIL);
        value.setNormalizedContact(normalizedContact);
        value.setStatus(InventoryRestockSubscription.Status.ACTIVE);
        return value;
    }
}
