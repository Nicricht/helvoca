package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.delivery.DeliveryCoverageService;
import cl.helvoca.delivery.DeliveryWorkflowService;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowcaseSelectedProductQuoteToolTest {
    @Mock CatalogItemRepository catalog;
    @Mock CatalogMediaRepository catalogMedia;
    @Mock DeliveryZoneRepository deliveryZones;
    @Mock BusinessOrderRepository orders;
    @Mock BusinessOrderLineRepository orderLines;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessOperationCapabilityService capabilities;
    @Mock OrderWorkflowService orderWorkflow;
    @Mock DeliveryWorkflowService deliveryWorkflow;
    @Mock UniversalOperationWorkflowService universalOperations;
    @Mock PaymentWorkflowService paymentWorkflow;
    @Mock ConversationStateService conversationState;

    private CommercialOperationToolService service;

    @BeforeEach
    void setUp() {
        service = new CommercialOperationToolService(
                catalog,
                catalogMedia,
                deliveryZones,
                new DeliveryCoverageService(deliveryZones),
                orders,
                orderLines,
                operations,
                capabilities,
                orderWorkflow,
                deliveryWorkflow,
                universalOperations,
                paymentWorkflow,
                conversationState);
    }

    @Test
    void quotesBackendPriceOnSameOperationAndPreservesSelectionContext() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        BusinessOperation operation = selectedOperation(
                businessId, customerId, operationId, selectedId);
        operation.getMetadata().put("existingKey", "keep-me");

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(selectedId, businessId))
                .thenReturn(Optional.of(item(selectedId, businessId, "Producto B", "12990")));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("quantity", 2));

        assertTrue(result.getBoolean("success"), result::toString);
        JSONObject data = result.getJSONObject("data");
        assertEquals(operationId.toString(), data.getString("operationId"));
        assertEquals(selectedId.toString(), data.getString("selectedCatalogItemId"));
        assertEquals(2, data.getInt("quantity"));
        assertEquals(0, new BigDecimal(String.valueOf(data.get("unitPrice")))
                .compareTo(new BigDecimal("12990")));
        assertEquals(0, new BigDecimal(String.valueOf(data.get("total")))
                .compareTo(new BigDecimal("25980")));
        assertEquals("QUOTE_PENDING", data.getString("commercialStage"));
        assertFalse(data.getBoolean("idempotent"));

        assertEquals("keep-me", operation.getMetadata().get("existingKey"));
        assertEquals(selectedId.toString(), operation.getMetadata().get("selectedCatalogItemId"));
        assertEquals(selectedId.toString(), operation.getMetadata().get("quotedCatalogItemId"));
        assertEquals("QUOTE_PENDING", operation.getMetadata().get("commercialStage"));
        assertEquals("SELECTED_PRODUCT_QUOTED", operation.getMetadata().get("lastAction"));
        assertEquals(0, operation.getTotal().compareTo(new BigDecimal("25980")));
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void repeatedIdenticalQuoteIsIdempotent() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        BusinessOperation operation = selectedOperation(
                businessId, customerId, operationId, selectedId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(selectedId, businessId))
                .thenReturn(Optional.of(item(selectedId, businessId, "Producto B", "5000")));

        JSONObject args = new JSONObject()
                .put("operationId", operationId.toString())
                .put("quantity", 3);

        JSONObject first = execute(businessId, customerId, args);
        int revisionAfterFirst = operation.getRevision();
        JSONObject second = execute(businessId, customerId, args);

        assertTrue(first.getBoolean("success"));
        assertFalse(first.getJSONObject("data").getBoolean("idempotent"));
        assertTrue(second.getBoolean("success"));
        assertTrue(second.getJSONObject("data").getBoolean("idempotent"));
        assertEquals(revisionAfterFirst, operation.getRevision());
        verify(operations, times(1)).saveAndFlush(operation);
    }

    @Test
    void refusesToQuoteWithoutAuthoritativeSelection() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setMetadata(new LinkedHashMap<>());

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject().put("operationId", operationId.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("SHOWCASE_SELECTION_REQUIRED",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(catalog);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void refusesToInventPriceWhenCatalogHasNoPrice() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        BusinessOperation operation = selectedOperation(
                businessId, customerId, operationId, selectedId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(selectedId, businessId))
                .thenReturn(Optional.of(item(selectedId, businessId, "Sin precio", null)));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject().put("operationId", operationId.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CATALOG_PRICE_UNAVAILABLE",
                result.getJSONObject("error").getString("code"));
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidQuantityAndOtherCustomer() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        BusinessOperation operation = selectedOperation(
                businessId, customerId, operationId, selectedId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(selectedId, businessId))
                .thenReturn(Optional.of(item(selectedId, businessId, "Producto", "1000")));

        JSONObject invalidQuantity = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("quantity", 0));
        assertFalse(invalidQuantity.getBoolean("success"));
        assertEquals("INVALID_QUANTITY",
                invalidQuantity.getJSONObject("error").getString("code"));

        JSONObject otherCustomer = execute(
                businessId,
                UUID.randomUUID(),
                new JSONObject().put("operationId", operationId.toString()));
        assertFalse(otherCustomer.getBoolean("success"));
        assertEquals("OPERATION_NOT_OWNED",
                otherCustomer.getJSONObject("error").getString("code"));
    }

    private void allow(UUID businessId) {
        when(capabilities.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_QUOTE_TOOL))
                .thenReturn(true);
    }

    private JSONObject execute(UUID businessId, UUID customerId, JSONObject args) {
        return new JSONObject(service.execute(
                businessId,
                customerId,
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_QUOTE_TOOL,
                args.toString()));
    }

    private static BusinessOperation selectedOperation(UUID businessId,
                                                       UUID customerId,
                                                       UUID operationId,
                                                       UUID selectedId) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("showcaseCatalogItemIds", List.of(selectedId.toString()));
        metadata.put("selectedCatalogItemId", selectedId.toString());
        metadata.put("commercialStage", "PRODUCT_SELECTED");
        metadata.put("lastAction", "PRODUCT_SELECTED");
        operation.setMetadata(metadata);
        return operation;
    }

    private static CatalogItem item(UUID id,
                                    UUID businessId,
                                    String name,
                                    String price) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setPrice(price == null ? null : new BigDecimal(price));
        item.setCurrency("CLP");
        item.setActive(true);
        return item;
    }
}
