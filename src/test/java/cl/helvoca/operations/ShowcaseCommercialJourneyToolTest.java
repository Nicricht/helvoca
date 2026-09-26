package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.delivery.DeliveryCoverageService;
import cl.helvoca.delivery.DeliveryWorkflowService;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowcaseCommercialJourneyToolTest {
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
        lenient().when(operations.saveAndFlush(any(BusinessOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void selectedProductCreatesLinkedOrderDraftWithoutLettingModelChoosePrice() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID confirmationToken = UUID.randomUUID();

        BusinessOperation journey = quotedJourney(
                businessId, customerId, journeyId, selectedId, 2);
        BusinessOperation orderOperation = orderOperation(
                businessId, customerId, orderOperationId, null);

        when(capabilities.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_ORDER_TOOL)).thenReturn(true);
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(orderOperation));
        when(orderWorkflow.quote(
                eq(businessId), eq(customerId), any(), eq("+56911111111"),
                eq(BusinessOrder.Source.WHATSAPP), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("revision", 1)
                        .put("confirmationToken", confirmationToken.toString())
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("total", new BigDecimal("25980"))
                        .put("currency", "CLP")
                        .put("fulfillmentType", "PICKUP")));

        JSONObject result = execute(
                businessId,
                customerId,
                CommercialOperationToolService.SHOWCASE_ORDER_TOOL,
                new JSONObject()
                        .put("operationId", journeyId.toString())
                        .put("fulfillmentType", "PICKUP"));

        assertTrue(result.getBoolean("success"), result::toString);
        JSONObject data = result.getJSONObject("data");
        assertEquals(journeyId.toString(), data.getString("commercialJourneyOperationId"));
        assertEquals(orderOperationId.toString(), data.getString("orderOperationId"));
        assertEquals(selectedId.toString(), data.getString("selectedCatalogItemId"));

        assertEquals(journeyId.toString(),
                orderOperation.getMetadata().get("commercialJourneyOperationId"));
        assertEquals(selectedId.toString(),
                orderOperation.getMetadata().get("selectedCatalogItemId"));
        assertEquals(orderOperationId.toString(),
                journey.getMetadata().get("orderOperationId"));
        assertEquals("PURCHASE_PENDING", journey.getMetadata().get("commercialStage"));
        assertEquals("ORDER_QUOTED", journey.getMetadata().get("lastAction"));

        ArgumentCaptor<JSONObject> args = ArgumentCaptor.forClass(JSONObject.class);
        verify(orderWorkflow).quote(
                eq(businessId), eq(customerId), any(), eq("+56911111111"),
                eq(BusinessOrder.Source.WHATSAPP), args.capture());

        JSONObject delegated = args.getValue();
        JSONArray items = delegated.getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals(selectedId.toString(),
                items.getJSONObject(0).getString("catalogItemId"));
        assertEquals(2, items.getJSONObject(0).getInt("quantity"));
        assertFalse(delegated.has("total"));
        assertFalse(delegated.has("unitPrice"));
    }

    @Test
    void repeatedPurchaseDraftReusesLinkedOrderOperationInsteadOfCreatingAnother() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();

        BusinessOperation journey = quotedJourney(
                businessId, customerId, journeyId, selectedId, 1);
        journey.getMetadata().put("orderOperationId", orderOperationId.toString());

        BusinessOperation existingOrder = orderOperation(
                businessId, customerId, orderOperationId, journeyId);
        existingOrder.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);

        when(capabilities.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_ORDER_TOOL)).thenReturn(true);
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(existingOrder));
        when(orderWorkflow.update(
                eq(businessId), eq(customerId), any(), eq("+56911111111"), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("revision", 2)
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("total", 5000)
                        .put("currency", "CLP")
                        .put("fulfillmentType", "PICKUP")));

        JSONObject result = execute(
                businessId,
                customerId,
                CommercialOperationToolService.SHOWCASE_ORDER_TOOL,
                new JSONObject()
                        .put("operationId", journeyId.toString())
                        .put("fulfillmentType", "PICKUP"));

        assertTrue(result.getBoolean("success"), result::toString);
        verify(orderWorkflow, never()).quote(any(), any(), any(), any(), any(), any());
        verify(orderWorkflow).update(
                eq(businessId), eq(customerId), any(), eq("+56911111111"),
                argThat(value -> orderOperationId.toString().equals(
                        value.optString("operationId"))));
    }

    @Test
    void confirmedOrderAdvancesOriginalJourney() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        BusinessOperation journey = bareJourney(businessId, customerId, journeyId);
        BusinessOperation orderOperation = orderOperation(
                businessId, customerId, orderOperationId, journeyId);

        when(capabilities.isToolAllowed(businessId, "create_order")).thenReturn(true);
        when(orderWorkflow.confirm(any(), any(), any(), any(), any(), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("orderId", orderId.toString())
                        .put("status", "CONFIRMED")
                        .put("total", 12990)
                        .put("currency", "CLP")));
        when(operations.findByIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(orderOperation));
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));

        JSONObject result = execute(
                businessId,
                customerId,
                "create_order",
                new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertTrue(result.getBoolean("success"));
        assertEquals(orderId.toString(), journey.getMetadata().get("orderId"));
        assertEquals(orderOperationId.toString(),
                journey.getMetadata().get("orderOperationId"));
        assertEquals("ORDER_CONFIRMED", journey.getMetadata().get("commercialStage"));
        assertEquals("ORDER_CONFIRMED", journey.getMetadata().get("lastAction"));
    }

    @Test
    void paymentDraftAndCheckoutRemainLinkedToOriginalJourney() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        BusinessOperation journey = bareJourney(businessId, customerId, journeyId);
        BusinessOperation orderOperation = orderOperation(
                businessId, customerId, orderOperationId, journeyId);
        orderOperation.setStatus(BusinessOperation.Status.CONFIRMED);

        BusinessOperation paymentOperation = new BusinessOperation();
        paymentOperation.setId(paymentOperationId);
        paymentOperation.setBusinessId(businessId);
        paymentOperation.setCustomerId(customerId);
        paymentOperation.setType(BusinessOperation.Type.PAYMENT);
        paymentOperation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        paymentOperation.setMetadata(new LinkedHashMap<>());

        when(capabilities.isToolAllowed(businessId, "quote_payment")).thenReturn(true);
        when(paymentWorkflow.quote(any(), any(), any(), any(), any(), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("targetOperationId", orderOperationId.toString())
                        .put("amount", 12990)
                        .put("currency", "CLP")
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("confirmationToken", UUID.randomUUID().toString())));
        when(operations.findByIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(orderOperation));
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));

        JSONObject quotePayment = execute(
                businessId,
                customerId,
                "quote_payment",
                new JSONObject().put("targetOperationId", orderOperationId.toString()));

        assertTrue(quotePayment.getBoolean("success"));
        assertEquals(journeyId.toString(),
                paymentOperation.getMetadata().get("commercialJourneyOperationId"));
        assertEquals(paymentOperationId.toString(),
                journey.getMetadata().get("paymentOperationId"));
        assertEquals("PAYMENT_PENDING", journey.getMetadata().get("commercialStage"));

        when(capabilities.isToolAllowed(businessId, "create_payment")).thenReturn(true);
        when(paymentWorkflow.confirm(any(), any(), any(), any(), any(), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("paymentId", paymentId.toString())
                        .put("targetOperationId", orderOperationId.toString())
                        .put("provider", "mercadopago")
                        .put("status", "REQUIRES_ACTION")
                        .put("amount", 12990)
                        .put("currency", "CLP")
                        .put("checkoutUrl", "https://pay.example.test/checkout")));

        JSONObject created = execute(
                businessId,
                customerId,
                "create_payment",
                new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertTrue(created.getBoolean("success"));
        assertEquals(paymentId.toString(), journey.getMetadata().get("paymentId"));
        assertEquals("REQUIRES_ACTION", journey.getMetadata().get("paymentStatus"));
        assertEquals("PAYMENT_LINK_SENT", journey.getMetadata().get("commercialStage"));
        assertEquals("https://pay.example.test/checkout",
                journey.getMetadata().get("checkoutUrl"));
    }

    @Test
    void refreshedPaymentKeepsCommercialJourneyLinkage() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();

        BusinessOperation journey = bareJourney(businessId, customerId, journeyId);
        journey.getMetadata().put("orderOperationId", orderOperationId.toString());
        journey.getMetadata().put("paymentOperationId", paymentOperationId.toString());

        BusinessOperation orderOperation = orderOperation(
                businessId, customerId, orderOperationId, journeyId);
        orderOperation.setStatus(BusinessOperation.Status.CONFIRMED);

        BusinessOperation paymentOperation = new BusinessOperation();
        paymentOperation.setId(paymentOperationId);
        paymentOperation.setBusinessId(businessId);
        paymentOperation.setCustomerId(customerId);
        paymentOperation.setType(BusinessOperation.Type.PAYMENT);
        paymentOperation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        paymentOperation.setMetadata(new LinkedHashMap<>(java.util.Map.of(
                "targetOperationId", orderOperationId.toString())));

        when(capabilities.isToolAllowed(businessId, "update_payment")).thenReturn(true);
        when(paymentWorkflow.update(any(), any(), any(), any(), any(), any()))
                .thenReturn(success(new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("targetOperationId", orderOperationId.toString())
                        .put("amount", 12990)
                        .put("currency", "CLP")
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("confirmationToken", UUID.randomUUID().toString())));
        when(operations.findByIdAndBusinessId(orderOperationId, businessId))
                .thenReturn(Optional.of(orderOperation));
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));

        JSONObject result = execute(
                businessId,
                customerId,
                "update_payment",
                new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("targetOperationId", orderOperationId.toString()));

        assertTrue(result.getBoolean("success"), result::toString);
        assertEquals(journeyId.toString(),
                paymentOperation.getMetadata().get("commercialJourneyOperationId"));
        assertEquals(paymentOperationId.toString(),
                journey.getMetadata().get("paymentOperationId"));
        assertEquals("PAYMENT_PENDING", journey.getMetadata().get("commercialStage"));
        assertEquals("PAYMENT_REQUOTED", journey.getMetadata().get("lastAction"));
    }

    private JSONObject execute(UUID businessId,
                               UUID customerId,
                               String toolName,
                               JSONObject args) {
        return new JSONObject(service.execute(
                businessId,
                customerId,
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                toolName,
                args.toString()));
    }

    private static BusinessOperation quotedJourney(UUID businessId,
                                                   UUID customerId,
                                                   UUID journeyId,
                                                   UUID selectedId,
                                                   int quantity) {
        BusinessOperation operation = bareJourney(businessId, customerId, journeyId);
        operation.getMetadata().put("showcaseCatalogItemIds", List.of(selectedId.toString()));
        operation.getMetadata().put("selectedCatalogItemId", selectedId.toString());
        operation.getMetadata().put("quotedCatalogItemId", selectedId.toString());
        operation.getMetadata().put("quotedQuantity", quantity);
        operation.getMetadata().put("quotedTotal", new BigDecimal("12990")
                .multiply(BigDecimal.valueOf(quantity)));
        operation.getMetadata().put("quotedCurrency", "CLP");
        operation.getMetadata().put("commercialStage", "QUOTE_PENDING");
        return operation;
    }

    private static BusinessOperation bareJourney(UUID businessId,
                                                 UUID customerId,
                                                 UUID journeyId) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(journeyId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        operation.setMetadata(new LinkedHashMap<>());
        return operation;
    }

    private static BusinessOperation orderOperation(UUID businessId,
                                                    UUID customerId,
                                                    UUID orderOperationId,
                                                    UUID journeyId) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(orderOperationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "ORDER");
        if (journeyId != null) {
            metadata.put("commercialJourneyOperationId", journeyId.toString());
        }
        operation.setMetadata(metadata);
        return operation;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL);
    }
}
