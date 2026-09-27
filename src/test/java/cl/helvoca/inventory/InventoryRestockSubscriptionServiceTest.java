package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryRestockSubscriptionServiceTest {
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

        CatalogItem product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Zapatilla");
        product.setActive(true);
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));

        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(businessId);
        stock.setCatalogItemId(productId);
        stock.setTrackingEnabled(true);
        stock.setOnHand(0);
        stock.setReserved(0);
        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId))
                .thenReturn(Optional.of(stock));

        when(subscriptions.saveAndFlush(any(InventoryRestockSubscription.class)))
                .thenAnswer(invocation -> {
                    InventoryRestockSubscription value = invocation.getArgument(0);
                    if (value.getId() == null) value.setId(UUID.randomUUID());
                    return value;
                });
        when(notifications.saveAndFlush(any(InventoryRestockNotification.class)))
                .thenAnswer(invocation -> {
                    InventoryRestockNotification value = invocation.getArgument(0);
                    if (value.getId() == null) value.setId(UUID.randomUUID());
                    return value;
                });
    }

    @Test
    void explicitConsentIsRequired() {
        var request = new InventoryRestockSubscriptionService.SubscribeRequest(
                null,
                productId,
                null,
                InventoryRestockSubscription.PreferredChannel.WHATSAPP,
                "+56911111111",
                false,
                "VOICE");

        assertThrows(IllegalArgumentException.class,
                () -> service.subscribeForBusiness(businessId, request));

        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void activeSubscriptionIsDeduplicatedBySubjectChannelAndNormalizedContact() {
        InventoryRestockSubscription existing = activeSubscription(null, "+56911111111");
        when(subscriptions.lockActiveDuplicate(
                businessId,
                productId,
                null,
                InventoryRestockSubscription.PreferredChannel.WHATSAPP,
                "+56911111111"))
                .thenReturn(Optional.of(existing));

        var request = new InventoryRestockSubscriptionService.SubscribeRequest(
                null,
                productId,
                null,
                InventoryRestockSubscription.PreferredChannel.WHATSAPP,
                "+56 9 1111 1111",
                true,
                "voice");

        var result = service.subscribeForBusiness(businessId, request);

        assertEquals(existing.getId(), result.id());
        assertEquals(InventoryRestockSubscription.Status.ACTIVE, result.status());
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void subscriptionIsRejectedWhenProductIsAlreadyAvailable() {
        InventoryStock available = new InventoryStock();
        available.setBusinessId(businessId);
        available.setCatalogItemId(productId);
        available.setTrackingEnabled(true);
        available.setOnHand(2);
        available.setReserved(0);
        when(stocks.findByBusinessIdAndCatalogItemId(businessId, productId))
                .thenReturn(Optional.of(available));

        var request = new InventoryRestockSubscriptionService.SubscribeRequest(
                null,
                productId,
                null,
                InventoryRestockSubscription.PreferredChannel.WHATSAPP,
                "+56911111111",
                true,
                "VOICE");

        assertThrows(IllegalArgumentException.class,
                () -> service.subscribeForBusiness(businessId, request));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void restockQueuesOnePendingNotificationAndMarksSubscriptionNotified() {
        InventoryRestockSubscription active = activeSubscription(null, "+56911111111");
        when(subscriptions.lockActiveForSubject(businessId, productId, null))
                .thenReturn(List.of(active));
        when(notifications.findByBusinessIdAndSubscriptionId(businessId, active.getId()))
                .thenReturn(Optional.empty());

        int queued = service.onRestocked(
                businessId, productId, null, "Zapatilla", "SHOE-01", 7);

        assertEquals(1, queued);
        ArgumentCaptor<InventoryRestockNotification> captured =
                ArgumentCaptor.forClass(InventoryRestockNotification.class);
        verify(notifications).saveAndFlush(captured.capture());

        InventoryRestockNotification notification = captured.getValue();
        assertEquals(active.getId(), notification.getSubscriptionId());
        assertEquals(productId, notification.getCatalogItemId());
        assertNull(notification.getVariantId());
        assertEquals(InventoryRestockNotification.Status.PENDING, notification.getStatus());
        assertEquals("inventory-restock:" + active.getId(), notification.getIdempotencyKey());
        assertEquals("+56911111111", notification.getContact());
        assertEquals(7, notification.getAvailable());

        assertEquals(InventoryRestockSubscription.Status.NOTIFIED, active.getStatus());
        assertNotNull(active.getNotifiedAt());
        verify(subscriptions).saveAndFlush(active);
    }

    @Test
    void restockKeepsVariantIdentityExact() {
        UUID variantId = UUID.randomUUID();
        InventoryRestockSubscription active = activeSubscription(variantId, "+56922222222");
        when(subscriptions.lockActiveForSubject(businessId, productId, variantId))
                .thenReturn(List.of(active));
        when(notifications.findByBusinessIdAndSubscriptionId(businessId, active.getId()))
                .thenReturn(Optional.empty());

        service.onRestocked(
                businessId, productId, variantId, "Zapatilla · Negro / 42", "SHOE-BLK-42", 3);

        ArgumentCaptor<InventoryRestockNotification> captured =
                ArgumentCaptor.forClass(InventoryRestockNotification.class);
        verify(notifications).saveAndFlush(captured.capture());
        assertEquals(variantId, captured.getValue().getVariantId());
    }

    @Test
    void cancellingNotifiedSubscriptionCancelsPendingQueueRecord() {
        UUID subscriptionId = UUID.randomUUID();
        InventoryRestockSubscription subscription = activeSubscription(null, "+56933333333");
        subscription.setId(subscriptionId);
        subscription.setStatus(InventoryRestockSubscription.Status.NOTIFIED);
        subscription.setNotifiedAt(java.time.Instant.now());

        InventoryRestockNotification notification = new InventoryRestockNotification();
        notification.setId(UUID.randomUUID());
        notification.setBusinessId(businessId);
        notification.setSubscriptionId(subscriptionId);
        notification.setCatalogItemId(productId);
        notification.setPreferredChannel(InventoryRestockSubscription.PreferredChannel.WHATSAPP);
        notification.setContact("+56933333333");
        notification.setSubjectName("Zapatilla");
        notification.setAvailable(2);
        notification.setStatus(InventoryRestockNotification.Status.PENDING);
        notification.setIdempotencyKey("inventory-restock:" + subscriptionId);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(subscriptions.lockByIdAndBusinessId(subscriptionId, businessId))
                .thenReturn(Optional.of(subscription));
        when(notifications.findByBusinessIdAndSubscriptionId(businessId, subscriptionId))
                .thenReturn(Optional.of(notification));

        var result = service.cancel(subscriptionId);

        assertEquals(InventoryRestockSubscription.Status.CANCELLED, result.status());
        assertNotNull(result.cancelledAt());
        assertEquals(InventoryRestockNotification.Status.CANCELLED, notification.getStatus());
        assertNotNull(notification.getCancelledAt());
        verify(notifications).saveAndFlush(notification);
        verify(subscriptions).saveAndFlush(subscription);
    }

    private InventoryRestockSubscription activeSubscription(UUID variantId, String normalizedContact) {
        InventoryRestockSubscription value = new InventoryRestockSubscription();
        value.setId(UUID.randomUUID());
        value.setBusinessId(businessId);
        value.setCatalogItemId(productId);
        value.setVariantId(variantId);
        value.setPreferredChannel(InventoryRestockSubscription.PreferredChannel.WHATSAPP);
        value.setContact(normalizedContact);
        value.setNormalizedContact(normalizedContact);
        value.setConsentGranted(true);
        value.setConsentGrantedAt(java.time.Instant.now());
        value.setConsentSource("VOICE");
        value.setStatus(InventoryRestockSubscription.Status.ACTIVE);
        return value;
    }
}
