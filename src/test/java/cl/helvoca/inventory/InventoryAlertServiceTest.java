package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InventoryAlertServiceTest {
    private InventoryAlertRepository alerts;
    private CatalogItemRepository catalog;
    private TenantProvider tenant;
    private JdbcTemplate jdbc;
    private InventoryRestockSubscriptionService restockSubscriptions;
    private InventoryAlertService service;
    private UUID businessId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        alerts = mock(InventoryAlertRepository.class);
        catalog = mock(CatalogItemRepository.class);
        tenant = mock(TenantProvider.class);
        jdbc = mock(JdbcTemplate.class);
        restockSubscriptions = mock(InventoryRestockSubscriptionService.class);
        service = new InventoryAlertService(alerts, catalog, tenant, jdbc, restockSubscriptions);

        businessId = UUID.randomUUID();
        productId = UUID.randomUUID();

        CatalogItem product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Zapatilla");
        product.setActive(true);

        when(catalog.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(product));
        when(alerts.saveAndFlush(any(InventoryAlert.class)))
                .thenAnswer(invocation -> {
                    InventoryAlert value = invocation.getArgument(0);
                    if (value.getId() == null) value.setId(UUID.randomUUID());
                    return value;
                });
    }

    @Test
    void createsSingleLowStockAlertWhenThresholdIsCrossed() {
        InventoryStock stock = stock(3, 1, 2);
        when(alerts.lockOpenBase(businessId, productId)).thenReturn(Optional.empty());

        service.evaluateBase(stock);

        ArgumentCaptor<InventoryAlert> saved = ArgumentCaptor.forClass(InventoryAlert.class);
        verify(alerts).saveAndFlush(saved.capture());
        InventoryAlert alert = saved.getValue();
        assertEquals(InventoryAlert.Type.LOW_STOCK, alert.getType());
        assertEquals(InventoryAlert.Status.OPEN, alert.getStatus());
        assertEquals(2, alert.getAvailable());
        assertEquals("Zapatilla", alert.getSubjectName());
        assertEquals("SHOE-01", alert.getSku());
    }

    @Test
    void sameLowStockStateDoesNotCreateDuplicateAlert() {
        InventoryStock stock = stock(3, 1, 2);
        InventoryAlert current = alert(InventoryAlert.Type.LOW_STOCK, 2, 2);
        when(alerts.lockOpenBase(businessId, productId)).thenReturn(Optional.of(current));

        service.evaluateBase(stock);

        verify(alerts, never()).saveAndFlush(any());
    }

    @Test
    void lowStockTransitionsToOutOfStockExactlyOnce() {
        InventoryStock stock = stock(1, 1, 2);
        InventoryAlert current = alert(InventoryAlert.Type.LOW_STOCK, 1, 2);
        when(alerts.lockOpenBase(businessId, productId)).thenReturn(Optional.of(current));

        service.evaluateBase(stock);

        ArgumentCaptor<InventoryAlert> saved = ArgumentCaptor.forClass(InventoryAlert.class);
        verify(alerts, times(2)).saveAndFlush(saved.capture());
        assertEquals(InventoryAlert.Status.RESOLVED, saved.getAllValues().get(0).getStatus());
        assertEquals(InventoryAlert.Type.OUT_OF_STOCK, saved.getAllValues().get(1).getType());
        assertEquals(0, saved.getAllValues().get(1).getAvailable());
    }

    @Test
    void outOfStockTransitionsToRestockedWhenHealthyAgain() {
        InventoryStock stock = stock(7, 0, 2);
        InventoryAlert current = alert(InventoryAlert.Type.OUT_OF_STOCK, 0, 2);
        when(alerts.lockOpenBase(businessId, productId)).thenReturn(Optional.of(current));

        service.evaluateBase(stock);

        ArgumentCaptor<InventoryAlert> saved = ArgumentCaptor.forClass(InventoryAlert.class);
        verify(alerts, times(2)).saveAndFlush(saved.capture());
        InventoryAlert restored = saved.getAllValues().get(1);
        assertEquals(InventoryAlert.Type.RESTOCKED, restored.getType());
        assertEquals(7, restored.getAvailable());
        assertEquals(InventoryAlert.Status.OPEN, restored.getStatus());
        verify(restockSubscriptions).onRestocked(
                businessId, productId, null, "Zapatilla", "SHOE-01", 7);
    }

    @Test
    void acknowledgedAlertStaysOpenSoSameStateDoesNotNotifyAgain() {
        InventoryStock stock = stock(2, 1, 2);
        InventoryAlert current = alert(InventoryAlert.Type.LOW_STOCK, 1, 2);
        current.setAcknowledgedAt(java.time.Instant.now());
        when(alerts.lockOpenBase(businessId, productId)).thenReturn(Optional.of(current));

        service.evaluateBase(stock);

        verify(alerts, never()).saveAndFlush(any());
    }

    @Test
    void variantUsesIndependentAlertIdentityAndName() {
        UUID variantId = UUID.randomUUID();
        InventoryProductVariant variant = new InventoryProductVariant();
        variant.setId(variantId);
        variant.setBusinessId(businessId);
        variant.setCatalogItemId(productId);
        variant.setName("Negro / 42");
        variant.setSku("SHOE-BLK-42");
        variant.setTrackingEnabled(true);
        variant.setActive(true);
        variant.setOnHand(1);
        variant.setReserved(1);
        variant.setReorderThreshold(2);

        when(alerts.lockOpenVariant(businessId, productId, variantId))
                .thenReturn(Optional.empty());

        service.evaluateVariant(variant);

        ArgumentCaptor<InventoryAlert> saved = ArgumentCaptor.forClass(InventoryAlert.class);
        verify(alerts).saveAndFlush(saved.capture());
        InventoryAlert alert = saved.getValue();
        assertEquals(variantId, alert.getVariantId());
        assertEquals(InventoryAlert.Type.OUT_OF_STOCK, alert.getType());
        assertEquals("Zapatilla · Negro / 42", alert.getSubjectName());
    }

    private InventoryStock stock(int onHand, int reserved, int threshold) {
        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(businessId);
        stock.setCatalogItemId(productId);
        stock.setSku("SHOE-01");
        stock.setTrackingEnabled(true);
        stock.setOnHand(onHand);
        stock.setReserved(reserved);
        stock.setReorderThreshold(threshold);
        return stock;
    }

    private InventoryAlert alert(InventoryAlert.Type type, int available, int threshold) {
        InventoryAlert alert = new InventoryAlert();
        alert.setId(UUID.randomUUID());
        alert.setBusinessId(businessId);
        alert.setCatalogItemId(productId);
        alert.setType(type);
        alert.setStatus(InventoryAlert.Status.OPEN);
        alert.setSubjectName("Zapatilla");
        alert.setSku("SHOE-01");
        alert.setAvailable(available);
        alert.setReorderThreshold(threshold);
        return alert;
    }
}
