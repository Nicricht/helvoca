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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowcaseProductSelectionToolTest {
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
    void selectsSecondProductOnSameOperationAndPreservesMetadata() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();

        BusinessOperation operation = operation(
                businessId, customerId, operationId, firstId, secondId, thirdId);
        operation.getMetadata().put("existingKey", "keep-me");

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(secondId, businessId))
                .thenReturn(Optional.of(item(secondId, businessId, "Producto B")));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 2));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(operationId.toString(), data.getString("operationId"));
        assertEquals(2, data.getInt("selectionIndex"));
        assertEquals(secondId.toString(), data.getString("selectedCatalogItemId"));
        assertEquals("PRODUCT_SELECTED", data.getString("commercialStage"));
        assertFalse(data.getBoolean("idempotent"));
        assertEquals("Producto B", data.getJSONObject("product").getString("name"));

        assertEquals("keep-me", operation.getMetadata().get("existingKey"));
        assertEquals(secondId.toString(), operation.getMetadata().get("selectedCatalogItemId"));
        assertEquals("PRODUCT_SELECTED", operation.getMetadata().get("commercialStage"));
        assertEquals("PRODUCT_SELECTED", operation.getMetadata().get("lastAction"));
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void repeatedSelectionIsIdempotentAndDoesNotWriteTwice() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        BusinessOperation operation = operation(
                businessId, customerId, operationId, firstId, secondId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(secondId, businessId))
                .thenReturn(Optional.of(item(secondId, businessId, "Producto B")));

        JSONObject args = new JSONObject()
                .put("operationId", operationId.toString())
                .put("selectionIndex", 2);

        JSONObject first = execute(businessId, customerId, args);
        JSONObject second = execute(businessId, customerId, args);

        assertTrue(first.getBoolean("success"));
        assertFalse(first.getJSONObject("data").getBoolean("idempotent"));
        assertTrue(second.getBoolean("success"));
        assertTrue(second.getJSONObject("data").getBoolean("idempotent"));
        assertEquals(operationId.toString(), second.getJSONObject("data").getString("operationId"));
        verify(operations, times(1)).saveAndFlush(operation);
    }

    @Test
    void acceptsExplicitCatalogItemOnlyWhenItWasShown() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        BusinessOperation operation = operation(
                businessId, customerId, operationId, firstId, secondId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(firstId, businessId))
                .thenReturn(Optional.of(item(firstId, businessId, "Producto A")));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("catalogItemId", firstId.toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(1, result.getJSONObject("data").getInt("selectionIndex"));
        assertEquals(firstId.toString(),
                result.getJSONObject("data").getString("selectedCatalogItemId"));
    }

    @Test
    void rejectsSelectionIndexOutsideLastShowcase() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOperation operation = operation(
                businessId, customerId, operationId, UUID.randomUUID(), UUID.randomUUID());

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 3));

        assertFalse(result.getBoolean("success"));
        assertEquals("SHOWCASE_SELECTION_OUT_OF_RANGE",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(catalog);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCatalogItemThatWasNotShown() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID notShownId = UUID.randomUUID();
        BusinessOperation operation = operation(
                businessId, customerId, operationId, firstId, secondId);

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("catalogItemId", notShownId.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CATALOG_ITEM_NOT_IN_SHOWCASE",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(catalog);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOperationOwnedByAnotherCustomer() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOperation operation = operation(
                businessId, UUID.randomUUID(), operationId, UUID.randomUUID());

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.of(operation));

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 1));

        assertFalse(result.getBoolean("success"));
        assertEquals("OPERATION_NOT_OWNED",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(catalog);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void tenantIsolationFailsClosedWhenOperationIsNotInCurrentBusiness() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        allow(businessId);
        when(operations.findByIdAndBusinessId(operationId, businessId))
                .thenReturn(Optional.empty());

        JSONObject result = execute(
                businessId,
                customerId,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 1));

        assertFalse(result.getBoolean("success"));
        assertEquals("OPERATION_NOT_FOUND",
                result.getJSONObject("error").getString("code"));
        verify(operations).findByIdAndBusinessId(operationId, businessId);
        verifyNoInteractions(catalog);
        verify(operations, never()).saveAndFlush(any());
    }

    private void allow(UUID businessId) {
        when(capabilities.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_SELECTION_TOOL))
                .thenReturn(true);
    }

    private JSONObject execute(UUID businessId, UUID customerId, JSONObject args) {
        return new JSONObject(service.execute(
                businessId,
                customerId,
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_SELECTION_TOOL,
                args.toString()));
    }

    private static BusinessOperation operation(UUID businessId,
                                               UUID customerId,
                                               UUID operationId,
                                               UUID... showcaseIds) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handoffChannel", "WHATSAPP");
        metadata.put("commercialStage", "MEDIA_QUEUED");
        metadata.put("showcaseCatalogItemIds",
                List.of(showcaseIds).stream().map(UUID::toString).toList());
        metadata.put("showcaseMessageCount", showcaseIds.length);
        metadata.put("lastAction", "PRODUCT_SHOWCASE_QUEUED");
        operation.setMetadata(metadata);
        return operation;
    }

    private static CatalogItem item(UUID id, UUID businessId, String name) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setCurrency("CLP");
        item.setActive(true);
        return item;
    }
}
