package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.ConversationStateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentWebhookChaosCertificationTest {

    @Test
    void providerFailureThenRetryThenDuplicateAppliesVerifiedPaymentOnce() {
        UUID businessId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        ConversationStateService conversation = mock(ConversationStateService.class);
        PaymentSuccessNotificationService notifications = mock(PaymentSuccessNotificationService.class);
        PaymentProviderAdapter adapter = mock(PaymentProviderAdapter.class);

        AtomicReference<PaymentWebhookEvent> eventState = new AtomicReference<>();
        when(events.findByBusinessIdAndProviderAndEventId(
                businessId, "mercadopago", "evt-chaos"))
                .thenAnswer(ignored -> Optional.ofNullable(eventState.get()));
        when(events.claim(any(), eq(businessId), eq("mercadopago"), eq("evt-chaos"),
                eq("ORDCHAOS123"), any()))
                .thenAnswer(ignored -> {
                    if (eventState.get() != null) return 0;
                    PaymentWebhookEvent claimed = new PaymentWebhookEvent();
                    claimed.setBusinessId(businessId);
                    claimed.setProvider("mercadopago");
                    claimed.setEventId("evt-chaos");
                    claimed.setExternalId("ORDCHAOS123");
                    claimed.setStatus(PaymentWebhookEvent.Status.RECEIVED);
                    eventState.set(claimed);
                    return 1;
                });
        when(events.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    PaymentWebhookEvent value = invocation.getArgument(0);
                    eventState.set(value);
                    return value;
                });

        BusinessPayment payment = new BusinessPayment();
        payment.setId(paymentId);
        payment.setBusinessId(businessId);
        payment.setOperationId(operationId);
        payment.setTargetOperationId(UUID.randomUUID());
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDCHAOS123");
        payment.setIdempotencyKey("payment-operation:" + operationId);
        payment.setAmount(new BigDecimal("15990"));
        payment.setCurrency("CLP");
        payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);

        when(payments.findByBusinessIdAndProviderIgnoreCaseAndExternalId(
                businessId, "mercadopago", "ORDCHAOS123"))
                .thenReturn(Optional.of(payment));
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(operations.findByIdAndBusinessId(any(), eq(businessId))).thenReturn(Optional.empty());
        when(providers.byCode(businessId, "mercadopago")).thenReturn(Optional.of(adapter));
        when(adapter.getStatus(any()))
                .thenThrow(new IllegalStateException("provider timeout"))
                .thenReturn(new PaymentProviderAdapter.StatusResult(
                        BusinessPayment.Status.SUCCEEDED,
                        Map.of("remoteStatus", "processed")));

        PaymentWebhookService service = new PaymentWebhookService(
                events, payments, operations, providers, conversation, notifications);

        PaymentWebhookService.Result first = service.processVerified(
                businessId, "mercadopago", "evt-chaos", "ORDCHAOS123",
                operationId.toString(), "{\"id\":\"evt-chaos\"}");
        assertEquals(PaymentWebhookService.Result.FAILED, first);
        assertEquals(PaymentWebhookEvent.Status.FAILED, eventState.get().getStatus());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, payment.getStatus());

        PaymentWebhookService.Result second = service.processVerified(
                businessId, "mercadopago", "evt-chaos", "ORDCHAOS123",
                operationId.toString(), "{\"id\":\"evt-chaos\"}");
        assertEquals(PaymentWebhookService.Result.PROCESSED, second);
        assertEquals(PaymentWebhookEvent.Status.PROCESSED, eventState.get().getStatus());
        assertEquals(BusinessPayment.Status.SUCCEEDED, payment.getStatus());

        PaymentWebhookService.Result third = service.processVerified(
                businessId, "mercadopago", "evt-chaos", "ORDCHAOS123",
                operationId.toString(), "{\"id\":\"evt-chaos\"}");
        assertEquals(PaymentWebhookService.Result.DUPLICATE, third);

        verify(adapter, times(2)).getStatus(any());
        verify(payments, times(1)).saveAndFlush(payment);
        verify(notifications, times(1)).onVerifiedSuccess(payment);
        verifyNoInteractions(conversation);
    }
}
