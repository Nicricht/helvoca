package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.OperationPolicyService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWorkflowServiceTest {
    @Mock BusinessOperationRepository operations;
    @Mock BusinessPaymentRepository payments;
    @Mock PaymentProviderRegistry providers;
    @Mock PaymentProviderAdapter provider;
    @Mock ConversationStateService conversationState;

    private PaymentWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new PaymentWorkflowService(
                operations,
                payments,
                providers,
                new OperationPolicyService(),
                conversationState);

        lenient().when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> {
            BusinessOperation operation = invocation.getArgument(0);
            if (operation.getId() == null) operation.setId(UUID.randomUUID());
            return operation;
        });
        lenient().when(payments.saveAndFlush(any(BusinessPayment.class))).thenAnswer(invocation -> {
            BusinessPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) payment.setId(UUID.randomUUID());
            return payment;
        });
    }

    @Test
    void quoteUsesBackendAuthoritativeAmountAndNeverClientAmount() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessOperation target = payableTarget(businessId, customerId, sourceReferenceId, new BigDecimal("15990.00"));
        when(operations.findByIdAndBusinessId(target.getId(), businessId)).thenReturn(Optional.of(target));
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(businessId, target.getId()))
                .thenReturn(List.of());

        JSONObject result = service.quote(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                new JSONObject()
                        .put("targetOperationId", target.getId().toString())
                        .put("amount", 1));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(0, new BigDecimal(data.get("amount").toString()).compareTo(new BigDecimal("15990.00")));
        assertEquals("CLP", data.getString("currency"));
        assertEquals("AWAITING_CONFIRMATION", data.getString("status"));
        assertFalse(data.isNull("confirmationToken"));

        ArgumentCaptor<BusinessOperation> saved = ArgumentCaptor.forClass(BusinessOperation.class);
        verify(operations).saveAndFlush(saved.capture());
        assertEquals(BusinessOperation.Type.PAYMENT, saved.getValue().getType());
        assertEquals(0, saved.getValue().getTotal().compareTo(new BigDecimal("15990.00")));
        assertEquals(target.getId().toString(), saved.getValue().getMetadata().get("targetOperationId"));
        verifyNoInteractions(providers);
    }

    @Test
    void staleConfirmationTokenIsRejectedBeforeProviderCall() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessOperation target = payableTarget(businessId, customerId, sourceReferenceId, new BigDecimal("10000.00"));
        BusinessOperation paymentDraft = paymentDraft(
                businessId, customerId, sourceReferenceId, target, new BigDecimal("10000.00"));
        when(payments.findByOperationIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.of(paymentDraft));

        JSONObject result = service.confirm(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("operationId", paymentDraft.getId().toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("STALE_PAYMENT_CONFIRMATION", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(providers);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void changedAmountRotatesTokenAndRequiresSecondConfirmation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessOperation target = payableTarget(businessId, customerId, sourceReferenceId, new BigDecimal("18000.00"));
        BusinessOperation paymentDraft = paymentDraft(
                businessId, customerId, sourceReferenceId, target, new BigDecimal("15000.00"));
        UUID oldToken = paymentDraft.getConfirmationToken();

        when(payments.findByOperationIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.of(paymentDraft));
        when(operations.findByIdAndBusinessId(target.getId(), businessId)).thenReturn(Optional.of(target));
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(businessId, target.getId()))
                .thenReturn(List.of());

        JSONObject result = service.confirm(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                new JSONObject()
                        .put("operationId", paymentDraft.getId().toString())
                        .put("confirmationToken", oldToken.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("PAYMENT_TERMS_CHANGED", result.getJSONObject("error").getString("code"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(0, new BigDecimal(data.get("amount").toString()).compareTo(new BigDecimal("18000.00")));
        assertNotEquals(oldToken.toString(), data.getString("confirmationToken"));
        assertEquals(2, data.getInt("revision"));
        verifyNoInteractions(providers);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void missingMerchantProviderFailsClosedWithoutMaterializingPayment() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessOperation target = payableTarget(businessId, customerId, sourceReferenceId, new BigDecimal("12000.00"));
        BusinessOperation paymentDraft = paymentDraft(
                businessId, customerId, sourceReferenceId, target, new BigDecimal("12000.00"));

        when(payments.findByOperationIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.of(paymentDraft));
        when(operations.findByIdAndBusinessId(target.getId(), businessId)).thenReturn(Optional.of(target));
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(businessId, target.getId()))
                .thenReturn(List.of());
        when(providers.resolve(businessId)).thenReturn(Optional.empty());

        JSONObject result = service.confirm(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                confirmArgs(paymentDraft));

        assertFalse(result.getBoolean("success"));
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", result.getJSONObject("error").getString("code"));
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void confirmedDraftCreatesProviderIntentWithStableIdempotencyKey() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessOperation target = payableTarget(businessId, customerId, sourceReferenceId, new BigDecimal("22000.00"));
        BusinessOperation paymentDraft = paymentDraft(
                businessId, customerId, sourceReferenceId, target, new BigDecimal("22000.00"));

        when(payments.findByOperationIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(paymentDraft.getId(), businessId)).thenReturn(Optional.of(paymentDraft));
        when(operations.findByIdAndBusinessId(target.getId(), businessId)).thenReturn(Optional.of(target));
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(businessId, target.getId()))
                .thenReturn(List.of());
        when(providers.resolve(businessId)).thenReturn(Optional.of(provider));
        when(provider.providerCode()).thenReturn("sandbox");
        when(provider.create(any())).thenReturn(new PaymentProviderAdapter.CreateResult(
                "ext-123",
                "https://pay.example/checkout/123",
                BusinessPayment.Status.REQUIRES_ACTION,
                Map.of("mode", "test")));

        JSONObject result = service.confirm(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                confirmArgs(paymentDraft));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals("REQUIRES_ACTION", data.getString("status"));
        assertEquals("sandbox", data.getString("provider"));
        assertEquals("https://pay.example/checkout/123", data.getString("checkoutUrl"));
        assertFalse(data.getBoolean("idempotentReplay"));

        ArgumentCaptor<PaymentProviderAdapter.CreateCommand> command =
                ArgumentCaptor.forClass(PaymentProviderAdapter.CreateCommand.class);
        verify(provider).create(command.capture());
        assertEquals("payment-operation:" + paymentDraft.getId(), command.getValue().idempotencyKey());
        assertEquals(0, command.getValue().amount().compareTo(new BigDecimal("22000.00")));

        ArgumentCaptor<BusinessPayment> payment = ArgumentCaptor.forClass(BusinessPayment.class);
        verify(payments).saveAndFlush(payment.capture());
        assertEquals(target.getId(), payment.getValue().getTargetOperationId());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, payment.getValue().getStatus());
        assertEquals("payment-operation:" + paymentDraft.getId(), payment.getValue().getIdempotencyKey());
    }

    @Test
    void existingProjectionIsReturnedAsIdempotentReplayWithoutCallingProvider() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessPayment existing = new BusinessPayment();
        existing.setId(UUID.randomUUID());
        existing.setOperationId(UUID.randomUUID());
        existing.setBusinessId(businessId);
        existing.setCustomerId(customerId);
        existing.setSourceReferenceId(sourceReferenceId);
        existing.setTargetOperationId(UUID.randomUUID());
        existing.setContactPhone("+56911111111");
        existing.setProvider("sandbox");
        existing.setExternalId("ext-existing");
        existing.setIdempotencyKey("payment-operation:" + existing.getOperationId());
        existing.setAmount(new BigDecimal("5000.00"));
        existing.setCurrency("CLP");
        existing.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        existing.setSource(BusinessOrder.Source.VOICE);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(existing.getOperationId());
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(1);

        when(payments.findByOperationIdAndBusinessId(existing.getOperationId(), businessId))
                .thenReturn(Optional.of(existing));
        when(operations.findByIdAndBusinessId(existing.getOperationId(), businessId))
                .thenReturn(Optional.of(operation));

        JSONObject result = service.confirm(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("operationId", existing.getOperationId().toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertTrue(result.getBoolean("success"));
        assertTrue(result.getJSONObject("data").getBoolean("idempotentReplay"));
        verifyNoInteractions(providers);
    }

    @Test
    void providerStatusRefreshPropagatesSucceededToCommercialJourney() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();

        BusinessPayment payment = new BusinessPayment();
        payment.setId(UUID.randomUUID());
        payment.setOperationId(paymentOperationId);
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId);
        payment.setSourceReferenceId(sourceReferenceId);
        payment.setTargetOperationId(UUID.randomUUID());
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST123");
        payment.setIdempotencyKey("payment-operation:" + paymentOperationId);
        payment.setAmount(new BigDecimal("1000"));
        payment.setCurrency("CLP");
        payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        payment.setSource(BusinessOrder.Source.WHATSAPP);

        BusinessOperation paymentOperation = new BusinessOperation();
        paymentOperation.setId(paymentOperationId);
        paymentOperation.setBusinessId(businessId);
        paymentOperation.setCustomerId(customerId);
        paymentOperation.setType(BusinessOperation.Type.PAYMENT);
        paymentOperation.setStatus(BusinessOperation.Status.CONFIRMED);
        paymentOperation.setRevision(1);
        paymentOperation.setSource(BusinessOrder.Source.WHATSAPP);
        paymentOperation.setMetadata(new java.util.LinkedHashMap<>(Map.of(
                "commercialJourneyOperationId", journeyId.toString(),
                "paymentStatus", "REQUIRES_ACTION")));

        BusinessOperation journey = new BusinessOperation();
        journey.setId(journeyId);
        journey.setBusinessId(businessId);
        journey.setCustomerId(customerId);
        journey.setType(BusinessOperation.Type.REQUEST);
        journey.setStatus(BusinessOperation.Status.CONFIRMED);
        journey.setRevision(4);
        journey.setSource(BusinessOrder.Source.WHATSAPP);
        journey.setMetadata(new java.util.LinkedHashMap<>(Map.of(
                "commercialStage", "PAYMENT_LINK_SENT")));

        when(payments.findByIdAndBusinessId(payment.getId(), businessId))
                .thenReturn(Optional.of(payment));
        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(provider));
        when(provider.getStatus(any())).thenReturn(new PaymentProviderAdapter.StatusResult(
                BusinessPayment.Status.SUCCEEDED,
                Map.of("remoteStatus", "processed", "remoteStatusDetail", "accredited")));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));
        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));

        JSONObject result = service.status(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                new JSONObject().put("paymentId", payment.getId().toString()));

        assertTrue(result.getBoolean("success"), result::toString);
        assertEquals("SUCCEEDED", result.getJSONObject("data").getString("status"));
        assertEquals(BusinessPayment.Status.SUCCEEDED, payment.getStatus());
        assertEquals("SUCCEEDED", paymentOperation.getMetadata().get("paymentStatus"));
        assertEquals("PAID", journey.getMetadata().get("commercialStage"));
        assertEquals("SUCCEEDED", journey.getMetadata().get("paymentStatus"));
        assertEquals("PAYMENT_STATUS_VERIFIED", journey.getMetadata().get("lastAction"));
        assertEquals(5, journey.getRevision());
    }

    private static BusinessOperation payableTarget(UUID businessId,
                                                   UUID customerId,
                                                   UUID sourceReferenceId,
                                                   BigDecimal total) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(UUID.randomUUID());
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        operation.setContactName("Cliente");
        operation.setContactPhone("+56911111111");
        operation.setTotal(total);
        operation.setCurrency("CLP");
        return operation;
    }

    private static BusinessOperation paymentDraft(UUID businessId,
                                                  UUID customerId,
                                                  UUID sourceReferenceId,
                                                  BusinessOperation target,
                                                  BigDecimal amount) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(UUID.randomUUID());
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setContactName("Cliente");
        operation.setContactPhone("+56911111111");
        operation.setTotal(amount);
        operation.setCurrency("CLP");
        operation.setMetadata(Map.of(
                "intent", "PAYMENT",
                "targetOperationId", target.getId().toString(),
                "targetOperationType", target.getType().name(),
                "confirmationPending", true,
                "paymentPending", true));
        return operation;
    }

    private static JSONObject confirmArgs(BusinessOperation operation) {
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("confirmationToken", operation.getConfirmationToken().toString());
    }
}
