package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppAssistantReplyDeliveryServiceTest {

    @Test
    void deliveryDisabledDoesNotEnqueueJob() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(false);
        PersistentJobService jobs = mock(PersistentJobService.class);

        var service = new WhatsAppAssistantReplyDeliveryService(properties, jobs);
        service.scheduleMetaReply(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "wamid.test",
                MetaWhatsAppMessagingProvider.ID,
                "+56911111111",
                "Respuesta IA");

        verifyNoInteractions(jobs);
    }

    @Test
    void nonMetaTenantDoesNotEnqueueMetaJob() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        PersistentJobService jobs = mock(PersistentJobService.class);

        var service = new WhatsAppAssistantReplyDeliveryService(properties, jobs);
        service.scheduleMetaReply(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SM-test",
                "TWILIO_WHATSAPP",
                "+56911111111",
                "Respuesta IA");

        verifyNoInteractions(jobs);
    }

    @Test
    void enabledMetaReplyEnqueuesDurableIdempotentJob() {
        OutboundMessagingProperties properties = new OutboundMessagingProperties();
        properties.setDeliveryEnabled(true);
        PersistentJobService jobs = mock(PersistentJobService.class);

        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        var service = new WhatsAppAssistantReplyDeliveryService(properties, jobs);

        service.scheduleMetaReply(
                businessId,
                messageId,
                "wamid.inbound-1",
                MetaWhatsAppMessagingProvider.ID,
                "+56911111111",
                "Respuesta IA");

        verify(jobs).enqueue(
                eq(businessId),
                isNull(),
                eq(PersistentJob.Type.META_WHATSAPP_AI_REPLY),
                eq("meta-ai-reply:wamid.inbound-1"),
                argThat(payload -> messageId.toString().equals(
                        new JSONObject(payload).getString("messageId"))));
    }
}
