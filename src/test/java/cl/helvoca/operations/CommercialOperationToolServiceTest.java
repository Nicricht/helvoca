package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercialOperationToolServiceTest {
    @Mock CatalogItemRepository catalog;
    @Mock DeliveryZoneRepository deliveryZones;
    @Mock BusinessOrderRepository orders;
    @Mock BusinessOrderLineRepository orderLines;
    @Mock BusinessQuoteRepository quotes;
    @Mock BusinessLeadRepository leads;
    @Mock BusinessOperationCapabilityService capabilities;

    private CommercialOperationToolService service;

    @BeforeEach
    void setUp() {
        service = new CommercialOperationToolService(
                catalog, deliveryZones, orders, orderLines, quotes, leads, capabilities);
    }

    @Test
    void quoteOrderUsesTenantCatalogPricesAndAddressMatchedDeliveryFee() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        CatalogItem burger = item(businessId, itemId, "Hamburguesa", "5000");
        DeliveryZone zone = zone(businessId, zoneId, "Huechuraba", "Huechuraba; Pedro Fontova", "1500", "8000");

        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(burger));
        when(deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(zone));
        when(capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)).thenReturn(true);

        JSONObject args = orderArgs(itemId, 2, "DELIVERY")
                .put("address", "Av. Pedro Fontova 1234, Huechuraba");

        JSONObject result = new JSONObject(service.execute(
                businessId, null, UUID.randomUUID(), "+56911111111", BusinessOrder.Source.VOICE,
                "quote_order", args.toString()));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(new BigDecimal("10000"), decimal(data, "subtotal"));
        assertEquals(new BigDecimal("1500"), decimal(data, "deliveryFee"));
        assertEquals(new BigDecimal("11500"), decimal(data, "total"));
        assertEquals("CLP", data.getString("currency"));
        assertEquals("DELIVERY", data.getString("fulfillmentType"));
        assertEquals(zoneId.toString(), data.getString("deliveryZoneId"));
    }

    @Test
    void deliveryAddressOutsideConfiguredCoverageFailsClosed() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CatalogItem burger = item(businessId, itemId, "Hamburguesa", "5000");
        DeliveryZone zone = zone(businessId, UUID.randomUUID(), "Huechuraba", "Huechuraba; Pedro Fontova", "1500", "0");

        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(burger));
        when(deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(zone));
        when(capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)).thenReturn(true);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "quote_order", orderArgs(itemId, 1, "DELIVERY")
                        .put("address", "Providencia 123, Providencia")
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("INVALID_ARGUMENT", result.getJSONObject("error").getString("code"));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void validateDeliveryAddressReturnsBackendResolvedZone() {
        UUID businessId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, zoneId, "Santiago Norte", "Huechuraba; Quilicura", "1200", "6000");
        when(capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)).thenReturn(true);
        when(deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(zone));

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.VOICE,
                "validate_delivery_address",
                new JSONObject().put("address", "Los Libertadores 6500, Quilicura").toString()));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertTrue(data.getBoolean("covered"));
        assertEquals(zoneId.toString(), data.getString("deliveryZoneId"));
        assertEquals(new BigDecimal("1200"), decimal(data, "fee"));
    }

    @Test
    void createOrderRejectsTotalThatWasNotQuotedByBackend() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CatalogItem item = item(businessId, itemId, "Pizza", "9000");
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(item));

        JSONObject args = orderArgs(itemId, 1, "PICKUP")
                .put("expectedTotal", 1000);

        JSONObject result = new JSONObject(service.execute(
                businessId, UUID.randomUUID(), UUID.randomUUID(), "+56911111111", BusinessOrder.Source.VOICE,
                "create_order", args.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("ORDER_TOTAL_CHANGED", result.getJSONObject("error").getString("code"));
        verify(orders, never()).saveAndFlush(any());
        verify(orderLines, never()).save(any());
    }

    @Test
    void createOrderPersistsServerCalculatedSnapshotAfterExactConfirmation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CatalogItem item = item(businessId, itemId, "Completo italiano", "4500");
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(item));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(invocation -> {
            BusinessOrder order = invocation.getArgument(0);
            order.setId(orderId);
            return order;
        });
        when(orderLines.save(any(BusinessOrderLine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject args = orderArgs(itemId, 2, "PICKUP")
                .put("expectedTotal", 9000)
                .put("contactName", "Nico")
                .put("notes", "Llamar al estar listo");

        JSONObject result = new JSONObject(service.execute(
                businessId, customerId, UUID.randomUUID(), "+56911111111", BusinessOrder.Source.VOICE,
                "create_order", args.toString()));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(orderId.toString(), data.getString("orderId"));
        assertEquals(new BigDecimal("9000"), decimal(data, "total"));
        assertEquals("CONFIRMED", data.getString("status"));
        verify(orders).saveAndFlush(argThat(order ->
                businessId.equals(order.getBusinessId())
                        && customerId.equals(order.getCustomerId())
                        && "+56911111111".equals(order.getContactPhone())
                        && new BigDecimal("9000").compareTo(order.getTotal()) == 0));
        verify(orderLines).save(argThat(line ->
                itemId.equals(line.getCatalogItemId())
                        && line.getQuantity() == 2
                        && new BigDecimal("4500").compareTo(line.getUnitPrice()) == 0));
    }

    @Test
    void foreignCatalogItemCannotBeUsedByAnotherTenant() {
        UUID businessId = UUID.randomUUID();
        UUID foreignItemId = UUID.randomUUID();
        when(catalog.findByIdAndBusinessId(foreignItemId, businessId)).thenReturn(Optional.empty());

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "quote_order", orderArgs(foreignItemId, 1, "PICKUP").toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("INVALID_ARGUMENT", result.getJSONObject("error").getString("code"));
        verify(orders, never()).saveAndFlush(any());
    }

    private static JSONObject orderArgs(UUID itemId, int quantity, String fulfillment) {
        return new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", quantity)))
                .put("fulfillmentType", fulfillment);
    }

    private static CatalogItem item(UUID businessId, UUID id, String name, String price) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCurrency("CLP");
        item.setActive(true);
        return item;
    }

    private static DeliveryZone zone(UUID businessId, UUID id, String name,
                                     String coverageTerms, String fee, String minimum) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(id);
        zone.setBusinessId(businessId);
        zone.setName(name);
        zone.setCoverageTerms(coverageTerms);
        zone.setFee(new BigDecimal(fee));
        zone.setMinimumOrder(new BigDecimal(minimum));
        zone.setActive(true);
        return zone;
    }

    private static BigDecimal decimal(JSONObject object, String key) {
        return new BigDecimal(String.valueOf(object.get(key)));
    }
}
