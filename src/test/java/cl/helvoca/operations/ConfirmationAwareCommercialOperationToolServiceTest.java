package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryCoverageService;
import cl.helvoca.delivery.DeliveryWorkflowService;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmationAwareCommercialOperationToolServiceTest {
    @Mock CatalogItemRepository catalog;
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
    @Mock UniversalConfirmationService confirmations;
    @Mock OperationExecutionLockService executionLocks;
    @Mock CrossChannelMessagingToolService crossChannelMessaging;

    private ConfirmationAwareCommercialOperationToolService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmationAwareCommercialOperationToolService(
                catalog,
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
                conversationState,
                confirmations,
                executionLocks,
                crossChannelMessaging);
    }

    @Test
    void paymentConfirmationLocksBeforeAuthorizationAndProviderOwningWorkflow() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        when(capabilities.isToolAllowed(businessId, "create_payment")).thenReturn(true);
        when(confirmations.authorize(
                eq(businessId), eq(operationId), eq(customerId), isNull(), isNull(), eq(token)))
                .thenReturn(UniversalConfirmationService.Authorization.AUTHORIZED);
        when(paymentWorkflow.confirm(
                eq(businessId), eq(customerId), isNull(), isNull(), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class)))
                .thenReturn(new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject()
                                .put("operationId", operationId.toString())
                                .put("status", "REQUIRES_ACTION"))
                        .put("error", JSONObject.NULL));

        JSONObject result = new JSONObject(service.execute(
                businessId,
                customerId,
                null,
                null,
                BusinessOrder.Source.WHATSAPP,
                "create_payment",
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString())
                        .toString()));

        assertTrue(result.getBoolean("success"));

        InOrder order = inOrder(executionLocks, confirmations, paymentWorkflow);
        order.verify(executionLocks).lock(businessId, operationId);
        order.verify(confirmations).authorize(
                businessId, operationId, customerId, null, null, token);
        order.verify(paymentWorkflow).confirm(
                eq(businessId), eq(customerId), isNull(), isNull(), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class));
        verify(confirmations).recordResolution(
                businessId, operationId, token, BusinessOrder.Source.WHATSAPP, null);
    }

    @Test
    void nonConfirmationToolsDoNotAcquireOperationFence() {
        UUID businessId = UUID.randomUUID();
        when(capabilities.isToolAllowed(businessId, "list_catalog")).thenReturn(true);
        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(java.util.List.of());

        JSONObject result = new JSONObject(service.execute(
                businessId,
                null,
                null,
                null,
                BusinessOrder.Source.API,
                "list_catalog",
                "{}"));

        assertTrue(result.getBoolean("success"));
        verifyNoInteractions(executionLocks, confirmations, paymentWorkflow, crossChannelMessaging);
    }
}
