package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {
    @Mock CatalogItemRepository catalog;
    @Mock DeliveryZoneRepository deliveryZones;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessOperationItemRepository operationItems;
    @Mock BusinessOrderRepository orders;
    @Mock BusinessOrderLineRepository orderLines;
    @Mock BusinessOperationCapabilityService capabilities;

    private OrderWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new OrderWorkflowService(
                catalog, deliveryZones, operations, operationItems, orders, orderLines, capabilities);
    }

    @Test
    void quoteCalculatesPriceInBackendPersistsQuantityAndStructuredModifiersWithoutCreatingOrder() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CatalogItem burger = item(businessId, itemId, "Hamburguesa doble", "5000");
        AtomicReference<BusinessOperationItem> persistedDraftLine = new AtomicReference<>();

        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(burger));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> {
            BusinessOperation operation = invocation.getArgument(0);
            operation.setId(operationId);
            return operation;
        });
        when(operationItems.saveAll(any())).thenAnswer(invocation -> {
            Iterable<BusinessOperationItem> values = invocation.getArgument(0);
            BusinessOperationItem first = values.iterator().next();
            persistedDraftLine.set(first);
            return List.of(first);
        });

        JSONObject args = orderArgs(itemId, 2)
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", 2)
                        .put("modifiers", new JSONObject()
                                .put("remove", new JSONArray().put("cebolla"))
                                .put("options", new JSONObject().put("size", "grande")))));

        JSONObject result = service.quote(
                businessId, UUID.randomUUID(), UUID.randomUUID(), "+56911111111",
                BusinessOrder.Source.VOICE, args);

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(operationId.toString(), data.getString("operationId"));
        assertEquals(1, data.getInt("revision"));
        assertEquals("AWAITING_CONFIRMATION", data.getString("status"));
        assertEquals(new BigDecimal("10000"), decimal(data, "subtotal"));
        assertEquals(new BigDecimal("10000"), decimal(data, "total"));
        assertFalse(data.getString("confirmationToken").isBlank());

        BusinessOperationItem draftLine = persistedDraftLine.get();
        assertNotNull(draftLine);
        assertEquals(2, draftLine.getQuantity());
        assertEquals(new BigDecimal("5000"), draftLine.getUnitPrice());
        assertEquals(new BigDecimal("10000"), draftLine.getLineTotal());
        assertEquals(List.of("cebolla"), draftLine.getModifiers().get("remove"));
        assertEquals("grande", ((Map<?, ?>) draftLine.getModifiers().get("options")).get("size"));
        verify(orders, never()).saveAndFlush(any(BusinessOrder.class));
    }

    @Test
    void updateReplacesDraftStateAndInvalidatesPreviousConfirmation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID oldToken = UUID.randomUUID();
        CatalogItem cola = item(businessId, itemId, "Coca-Cola", "2000");
        BusinessOperation operation = operation(operationId, businessId, customerId, oldToken, "10000");

        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(cola));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject result = service.update(
                businessId, customerId, null, null,
                orderArgs(itemId, 1).put("operationId", operationId.toString()));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(2, data.getInt("revision"));
        assertEquals(new BigDecimal("2000"), decimal(data, "total"));
        assertNotEquals(oldToken.toString(), data.getString("confirmationToken"));
        assertEquals(new BigDecimal("2000"), operation.getTotal());
        assertEquals(BusinessOperation.Status.AWAITING_CONFIRMATION, operation.getStatus());
        verify(operationItems).deleteAllByOperationId(operationId);
    }

    @Test
    void staleConfirmationIsRejectedAndNeverCreatesOrder() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID currentToken = UUID.randomUUID();
        BusinessOperation operation = operation(operationId, businessId, customerId, currentToken, "9000");

        when(orders.findByOperationIdAndBusinessId(operationId, businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        JSONObject result = service.confirm(
                businessId, customerId, null, null, BusinessOrder.Source.WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("STALE_ORDER_CONFIRMATION", result.getJSONObject("error").getString("code"));
        verify(orders, never()).saveAndFlush(any(BusinessOrder.class));
        verify(orderLines, never()).save(any(BusinessOrderLine.class));
    }

    @Test
    void confirmCreatesImmutableSnapshotOnceAndRetryIsIdempotent() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CatalogItem item = item(businessId, itemId, "Completo italiano", "4500");
        BusinessOperation operation = operation(operationId, businessId, customerId, token, "9000");
        BusinessOperationItem storedLine = new BusinessOperationItem();
        storedLine.setOperationId(operationId);
        storedLine.setCatalogItemId(itemId);
        storedLine.setItemName(item.getName());
        storedLine.setQuantity(2);
        storedLine.setUnitPrice(new BigDecimal("4500"));
        storedLine.setLineTotal(new BigDecimal("9000"));
        storedLine.setModifiers(Map.of("remove", List.of("cebolla")));

        BusinessOrder persistedOrder = new BusinessOrder();
        AtomicReference<BusinessOrderLine> persistedLine = new AtomicReference<>();

        when(orders.findByOperationIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.empty(), Optional.of(persistedOrder));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operationItems.findAllByOperationIdOrderByCreatedAtAsc(operationId)).thenReturn(List.of(storedLine));
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(item));
        when(orders.saveAndFlush(any(BusinessOrder.class))).thenAnswer(invocation -> {
            BusinessOrder order = invocation.getArgument(0);
            order.setId(orderId);
            copyOrder(order, persistedOrder);
            return order;
        });
        when(orderLines.save(any(BusinessOrderLine.class))).thenAnswer(invocation -> {
            BusinessOrderLine line = invocation.getArgument(0);
            persistedLine.set(line);
            return line;
        });
        when(orderLines.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenAnswer(invocation -> List.of(persistedLine.get()));
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject confirmArgs = new JSONObject()
                .put("operationId", operationId.toString())
                .put("confirmationToken", token.toString());

        JSONObject first = service.confirm(
                businessId, customerId, null, null, BusinessOrder.Source.VOICE, confirmArgs);
        JSONObject second = service.confirm(
                businessId, customerId, null, null, BusinessOrder.Source.VOICE, confirmArgs);

        assertTrue(first.getBoolean("success"));
        assertFalse(first.getJSONObject("data").getBoolean("idempotentReplay"));
        assertTrue(second.getBoolean("success"));
        assertTrue(second.getJSONObject("data").getBoolean("idempotentReplay"));
        assertEquals(orderId.toString(), second.getJSONObject("data").getString("orderId"));

        BusinessOrderLine snapshot = persistedLine.get();
        assertNotNull(snapshot);
        assertEquals(2, snapshot.getQuantity());
        assertEquals(new BigDecimal("4500"), snapshot.getUnitPrice());
        assertEquals(new BigDecimal("9000"), snapshot.getLineTotal());
        assertEquals(List.of("cebolla"), snapshot.getModifiers().get("remove"));
        verify(orders, times(1)).saveAndFlush(any(BusinessOrder.class));
        verify(orderLines, times(1)).save(any(BusinessOrderLine.class));
    }

    @Test
    void foreignTenantCatalogItemCannotEnterDraft() {
        UUID businessId = UUID.randomUUID();
        UUID foreignItemId = UUID.randomUUID();
        when(catalog.findByIdAndBusinessId(foreignItemId, businessId)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.quote(
                businessId, null, UUID.randomUUID(), "+56911111111", BusinessOrder.Source.VOICE,
                orderArgs(foreignItemId, 1)));

        assertTrue(error.getMessage().contains("no existe"));
        verify(operations, never()).saveAndFlush(any(BusinessOperation.class));
    }

    private static JSONObject orderArgs(UUID itemId, int quantity) {
        return new JSONObject()
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", quantity)))
                .put("fulfillmentType", "PICKUP");
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

    private static BusinessOperation operation(UUID operationId,
                                               UUID businessId,
                                               UUID customerId,
                                               UUID confirmationToken,
                                               String total) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(1);
        operation.setConfirmationToken(confirmationToken);
        operation.setFulfillmentType(BusinessOrder.FulfillmentType.PICKUP);
        operation.setSubtotal(new BigDecimal(total));
        operation.setDeliveryFee(BigDecimal.ZERO);
        operation.setTotal(new BigDecimal(total));
        operation.setCurrency("CLP");
        return operation;
    }

    private static void copyOrder(BusinessOrder source, BusinessOrder target) {
        target.setId(source.getId());
        target.setOperationId(source.getOperationId());
        target.setBusinessId(source.getBusinessId());
        target.setCustomerId(source.getCustomerId());
        target.setSourceReferenceId(source.getSourceReferenceId());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setFulfillmentType(source.getFulfillmentType());
        target.setDeliveryZoneId(source.getDeliveryZoneId());
        target.setDeliveryAddress(source.getDeliveryAddress());
        target.setStatus(source.getStatus());
        target.setSubtotal(source.getSubtotal());
        target.setDeliveryFee(source.getDeliveryFee());
        target.setTotal(source.getTotal());
        target.setCurrency(source.getCurrency());
        target.setSource(source.getSource());
        target.setNotes(source.getNotes());
    }

    private static BigDecimal decimal(JSONObject object, String key) {
        return new BigDecimal(String.valueOf(object.get(key)));
    }
}
