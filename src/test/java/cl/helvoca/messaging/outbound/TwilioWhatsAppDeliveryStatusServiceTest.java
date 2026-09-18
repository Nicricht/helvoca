package cl.helvoca.messaging.outbound;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioWhatsAppDeliveryStatusServiceTest {

    @Test
    void deliveredStatusUpdatesTrackedMessage() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getProviderDeliveryStatus()).thenReturn("SENT");
        when(message.getDeliveredAt()).thenReturn(null);
        when(repository.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                TwilioWhatsAppMessagingProvider.ID, "SM123"))
                .thenReturn(Optional.of(message));

        var result = new TwilioWhatsAppDeliveryStatusService(repository)
                .apply("SM123", "delivered", null);

        assertEquals(TwilioWhatsAppDeliveryStatusService.Result.UPDATED, result);
        verify(message).setProviderDeliveryStatus("DELIVERED");
        verify(message).setDeliveryUpdatedAt(any(Instant.class));
        verify(message).setDeliveredAt(any(Instant.class));
        verify(message).setFailureCode(null);
        verify(repository).saveAndFlush(message);
    }

    @Test
    void readStatusDoesNotRegressToSentAfterwards() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getProviderDeliveryStatus()).thenReturn("READ");
        when(repository.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                TwilioWhatsAppMessagingProvider.ID, "SM123"))
                .thenReturn(Optional.of(message));

        var result = new TwilioWhatsAppDeliveryStatusService(repository)
                .apply("SM123", "sent", null);

        assertEquals(TwilioWhatsAppDeliveryStatusService.Result.IGNORED, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void undeliveredStatusCapturesProviderErrorWithoutChangingOutboundLifecycle() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getProviderDeliveryStatus()).thenReturn("SENT");
        when(repository.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                TwilioWhatsAppMessagingProvider.ID, "SM123"))
                .thenReturn(Optional.of(message));

        var result = new TwilioWhatsAppDeliveryStatusService(repository)
                .apply("SM123", "undelivered", "63016");

        assertEquals(TwilioWhatsAppDeliveryStatusService.Result.UPDATED, result);
        verify(message).setProviderDeliveryStatus("UNDELIVERED");
        verify(message).setFailureCode("TWILIO_63016");
        verify(message, never()).setStatus(any());
        verify(repository).saveAndFlush(message);
    }

    @Test
    void unknownMessageSidIsAcknowledgedWithoutMutation() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        when(repository.findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                TwilioWhatsAppMessagingProvider.ID, "SM404"))
                .thenReturn(Optional.empty());

        var result = new TwilioWhatsAppDeliveryStatusService(repository)
                .apply("SM404", "delivered", null);

        assertEquals(TwilioWhatsAppDeliveryStatusService.Result.NOT_FOUND, result);
        verify(repository, never()).saveAndFlush(any());
    }
}
