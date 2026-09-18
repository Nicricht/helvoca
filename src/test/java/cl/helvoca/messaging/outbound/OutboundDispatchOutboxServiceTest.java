package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboundDispatchOutboxServiceTest {

    @Test
    void manualRetryCreatesNewDurableJobAndResetsProviderAttemptState() {
        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        OutboundMessageRepository messages = mock(OutboundMessageRepository.class);
        PersistentJobService jobs = mock(PersistentJobService.class);
        OutboundMessage message = mock(OutboundMessage.class);

        when(message.getId()).thenReturn(messageId);
        when(message.getOperationId()).thenReturn(operationId);
        when(message.getStatus()).thenReturn(OutboundMessage.Status.SENT);
        when(message.getProviderDeliveryStatus()).thenReturn("UNDELIVERED");
        when(message.getRetryCount()).thenReturn(0);
        when(messages.findByIdAndBusinessId(messageId, businessId)).thenReturn(Optional.of(message));

        PersistentJob job = job(businessId, operationId,
                "outbound-message-dispatch:" + messageId + ":retry:1");
        when(jobs.enqueue(
                eq(businessId),
                eq(operationId),
                eq(PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH),
                eq("outbound-message-dispatch:" + messageId + ":retry:1"),
                anyString()))
                .thenReturn(job);

        PersistentJob result = new OutboundDispatchOutboxService(messages, jobs)
                .retry(businessId, messageId);

        assertSame(job, result);
        verify(message).setRetryCount(1);
        verify(message).setStatus(OutboundMessage.Status.QUEUED);
        verify(message).setSentAt(null);
        verify(message).setProviderMessageId(null);
        verify(message).setProviderDeliveryStatus("QUEUED");
        verify(message).setDeliveryUpdatedAt(any(Instant.class));
        verify(message).setDeliveredAt(null);
        verify(message).setReadAt(null);
        verify(message).setFailureCode(null);
        verify(messages).saveAndFlush(message);
    }

    @Test
    void manualRetryRejectsNonFailedDelivery() {
        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        OutboundMessageRepository messages = mock(OutboundMessageRepository.class);
        PersistentJobService jobs = mock(PersistentJobService.class);
        OutboundMessage message = mock(OutboundMessage.class);

        when(message.getStatus()).thenReturn(OutboundMessage.Status.SENT);
        when(message.getProviderDeliveryStatus()).thenReturn("DELIVERED");
        when(messages.findByIdAndBusinessId(messageId, businessId)).thenReturn(Optional.of(message));

        assertThrows(IllegalStateException.class, () ->
                new OutboundDispatchOutboxService(messages, jobs).retry(businessId, messageId));
        verifyNoInteractions(jobs);
    }

    private static PersistentJob job(UUID businessId, UUID operationId, String key) {
        Instant now = Instant.now();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                operationId,
                PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                PersistentJob.Status.PENDING,
                key,
                "{}",
                0,
                5,
                now,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }
}
