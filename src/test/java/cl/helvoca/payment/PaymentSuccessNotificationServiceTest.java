package cl.helvoca.payment;

import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundDispatchOutboxService;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentSuccessNotificationServiceTest {

    @Test
    void deliveryDisabledPreparesButNeverQueuesRealTraffic() {
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(false);
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        OutboundDispatchOutboxService outbox = mock(OutboundDispatchOutboxService.class);

        BusinessPayment payment = succeededPayment();
        OutboundMessage message = new OutboundMessage();
        when(outbound.prepare(
                payment.getBusinessId(),
                payment.getCustomerId(),
                OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_CONFIRMATION,
                payment.getOperationId(),
                null)).thenReturn(message);

        PaymentSuccessNotificationService service =
                new PaymentSuccessNotificationService(outbound, properties, providers, outbox);

        assertEquals(PaymentSuccessNotificationService.Result.PREPARED,
                service.onVerifiedSuccess(payment));
        verifyNoInteractions(providers);
        verifyNoInteractions(outbox);
    }

    @Test
    void nonSucceededPaymentIsSkippedBeforeOutboundPreparation() {
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        OutboundDispatchOutboxService outbox = mock(OutboundDispatchOutboxService.class);

        BusinessPayment payment = succeededPayment();
        payment.setStatus(BusinessPayment.Status.PENDING);

        PaymentSuccessNotificationService service =
                new PaymentSuccessNotificationService(outbound, properties, providers, outbox);

        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(payment));
        verifyNoInteractions(outbound, providers, outbox);
    }

    private static BusinessPayment succeededPayment() {
        BusinessPayment payment = new BusinessPayment();
        payment.setId(UUID.randomUUID());
        payment.setBusinessId(UUID.randomUUID());
        payment.setCustomerId(UUID.randomUUID());
        payment.setOperationId(UUID.randomUUID());
        payment.setStatus(BusinessPayment.Status.SUCCEEDED);
        return payment;
    }
    @Test
    void validatesAllRequiredPaymentIdentityFieldsBeforePreparingOutbound() {
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        OutboundDispatchOutboxService outbox = mock(OutboundDispatchOutboxService.class);
        PaymentSuccessNotificationService service =
                new PaymentSuccessNotificationService(outbound, properties, providers, outbox);

        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(null));

        BusinessPayment missingBusiness = succeededPayment();
        missingBusiness.setBusinessId(null);
        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(missingBusiness));

        BusinessPayment missingCustomer = succeededPayment();
        missingCustomer.setCustomerId(null);
        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(missingCustomer));

        BusinessPayment missingOperation = succeededPayment();
        missingOperation.setOperationId(null);
        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(missingOperation));

        verifyNoInteractions(outbound, providers, outbox);
    }

    @Test
    void deliveryEnabledQueuesPreparedConfirmationExactlyOnce() {
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        properties.setProvider("meta");
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        OutboundDispatchOutboxService outbox = mock(OutboundDispatchOutboxService.class);

        BusinessPayment payment = succeededPayment();
        OutboundMessage message = mock(OutboundMessage.class);
        UUID messageId = UUID.randomUUID();
        when(message.getId()).thenReturn(messageId);
        when(outbound.prepare(
                payment.getBusinessId(),
                payment.getCustomerId(),
                OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_CONFIRMATION,
                payment.getOperationId(),
                null)).thenReturn(message);

        PaymentSuccessNotificationService service =
                new PaymentSuccessNotificationService(outbound, properties, providers, outbox);

        assertEquals(PaymentSuccessNotificationService.Result.QUEUED,
                service.onVerifiedSuccess(payment));
        verify(providers).require("meta", OutboundMessage.Channel.WHATSAPP);
        verify(outbox).queue(payment.getBusinessId(), messageId);
    }

    @Test
    void outboundSafetyFailuresSkipWithoutBreakingVerifiedPayment() {
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        properties.setProvider("meta");
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        OutboundDispatchOutboxService outbox = mock(OutboundDispatchOutboxService.class);

        BusinessPayment payment = succeededPayment();
        when(outbound.prepare(any(), any(), any(), any(), any(), isNull()))
                .thenThrow(new IllegalStateException("recipient unavailable"));

        PaymentSuccessNotificationService service =
                new PaymentSuccessNotificationService(outbound, properties, providers, outbox);

        assertEquals(PaymentSuccessNotificationService.Result.SKIPPED,
                service.onVerifiedSuccess(payment));
        verifyNoInteractions(providers, outbox);
    }

}
