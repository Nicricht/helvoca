package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {
    @Test
    void verifiedWebhookRequeriesProviderAndSynchronizesUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();

        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        ConversationStateService conversation = mock(ConversationStateService.class);
        PaymentProviderAdapter adapter = mock(PaymentProviderAdapter.class);

        BusinessPayment payment = payment(paymentId, businessId, operationId, sourceReferenceId);
        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(1);

        PaymentWebhookEvent claimed = new PaymentWebhookEvent();
        claimed.setBusinessId(businessId);
        claimed.setProvider("mercadopago");
        claimed.setEventId("evt-1");
        claimed.setExternalId("ORDTST123");
        claimed.setStatus(PaymentWebhookEvent.Status.RECEIVED);
        when(events.findByBusinessIdAndProviderAndEventId(businessId, "mercadopago", "evt-1"))
                .thenReturn(Optional.empty(), Optional.of(claimed));
        when(events.claim(any(), eq(businessId), eq("mercadopago"), eq("evt-1"),
                eq("ORDTST123"), any())).thenReturn(1);
        when(events.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.findByBusinessIdAndProviderIgnoreCaseAndExternalId(
                businessId, "mercadopago", "ORDTST123")).thenReturn(Optional.of(payment));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(adapter));
        when(adapter.getStatus(any())).thenReturn(new PaymentProviderAdapter.StatusResult(
                BusinessPayment.Status.SUCCEEDED,
                Map.of("remoteStatus", "processed", "remoteStatusDetail", "accredited")));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(operations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentWebhookService service = new PaymentWebhookService(
                events, payments, operations, providers, conversation);
        PaymentWebhookService.Result result = service.processVerified(
                businessId,
                "mercadopago",
                "evt-1",
                "ORDTST123",
                operationId.toString(),
                "{\"id\":\"evt-1\"}");

        assertEquals(PaymentWebhookService.Result.PROCESSED, result);
        assertEquals(BusinessPayment.Status.SUCCEEDED, payment.getStatus());
        assertEquals("processed", payment.getMetadata().get("remoteStatus"));
        verify(adapter).getStatus(any());
        verify(conversation).apply(eq(businessId), eq(sourceReferenceId),
                eq(BusinessOrder.Source.WHATSAPP), eq(operationId), any());
    }

    @Test
    void concurrentDuplicateClaimDoesNotQueryProvider() {
        UUID businessId = UUID.randomUUID();
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        ConversationStateService conversation = mock(ConversationStateService.class);

        when(events.findByBusinessIdAndProviderAndEventId(
                businessId, "mercadopago", "evt-race")).thenReturn(Optional.empty());
        when(events.claim(any(), eq(businessId), eq("mercadopago"), eq("evt-race"),
                eq("ORDTST123"), any())).thenReturn(0);

        PaymentWebhookService service = new PaymentWebhookService(
                events, payments, operations, providers, conversation);
        PaymentWebhookService.Result result = service.processVerified(
                businessId, "mercadopago", "evt-race", "ORDTST123", null, "{}");

        assertEquals(PaymentWebhookService.Result.DUPLICATE, result);
        verifyNoInteractions(providers);
        verifyNoInteractions(payments);
    }

    @Test
    void duplicateProcessedEventDoesNotQueryProviderAgain() {
        UUID businessId = UUID.randomUUID();
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        ConversationStateService conversation = mock(ConversationStateService.class);

        PaymentWebhookEvent existing = new PaymentWebhookEvent();
        existing.setBusinessId(businessId);
        existing.setProvider("mercadopago");
        existing.setEventId("evt-duplicate");
        existing.setStatus(PaymentWebhookEvent.Status.PROCESSED);
        when(events.findByBusinessIdAndProviderAndEventId(
                businessId, "mercadopago", "evt-duplicate")).thenReturn(Optional.of(existing));

        PaymentWebhookService service = new PaymentWebhookService(
                events, payments, operations, providers, conversation);
        PaymentWebhookService.Result result = service.processVerified(
                businessId, "mercadopago", "evt-duplicate", "ORDTST123", null, "{}");

        assertEquals(PaymentWebhookService.Result.DUPLICATE, result);
        verifyNoInteractions(providers);
        verifyNoInteractions(payments);
    }

    private static BusinessPayment payment(UUID id,
                                           UUID businessId,
                                           UUID operationId,
                                           UUID sourceReferenceId) {
        BusinessPayment payment = new BusinessPayment();
        payment.setId(id);
        payment.setBusinessId(businessId);
        payment.setOperationId(operationId);
        payment.setTargetOperationId(UUID.randomUUID());
        payment.setSourceReferenceId(sourceReferenceId);
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST123");
        payment.setIdempotencyKey("payment-operation:" + operationId);
        payment.setAmount(new BigDecimal("1500"));
        payment.setCurrency("CLP");
        payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        payment.setSource(BusinessOrder.Source.WHATSAPP);
        return payment;
    }
}
