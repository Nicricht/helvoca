package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MetaWhatsAppInboundJobService {
    private final PersistentJobService jobs;
    private final MetaWhatsAppInboundProperties properties;

    public MetaWhatsAppInboundJobService(
            PersistentJobService jobs,
            MetaWhatsAppInboundProperties properties) {
        this.jobs = jobs;
        this.properties = properties;
    }

    public PersistentJob enqueueText(
            MetaWhatsAppTenantRoute route,
            MetaWhatsAppInboundMessage message) {
        requireRoute(route);
        if (message == null || blank(message.messageId()) || blank(message.from()) || blank(message.text())) {
            throw new IllegalArgumentException("Meta WhatsApp text message is incomplete");
        }

        UUID correlationId = MetaWhatsAppJobKeys.correlationId(route.businessId(), message.messageId());
        String payload = new JSONObject()
                .put("messageId", message.messageId())
                .put("phoneNumberId", route.phoneNumberId().toString())
                .put("from", message.from())
                .put("text", message.text())
                .put("correlationId", correlationId.toString())
                .toString();

        return jobs.enqueue(
                route.businessId(),
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS,
                MetaWhatsAppJobKeys.text(message.messageId()),
                payload,
                properties.getTextMaxAttempts(),
                Instant.now());
    }

    public PersistentJob enqueueAudio(
            MetaWhatsAppTenantRoute route,
            MetaWhatsAppInboundAudio message) {
        requireRoute(route);
        if (message == null || blank(message.messageId()) || blank(message.from()) || blank(message.mediaId())) {
            throw new IllegalArgumentException("Meta WhatsApp audio message is incomplete");
        }

        UUID correlationId = MetaWhatsAppJobKeys.correlationId(route.businessId(), message.messageId());
        JSONObject payload = new JSONObject()
                .put("messageId", message.messageId())
                .put("phoneNumberId", route.phoneNumberId().toString())
                .put("from", message.from())
                .put("mediaId", message.mediaId())
                .put("correlationId", correlationId.toString());
        if (!blank(message.mimeType())) payload.put("mimeType", message.mimeType());

        return jobs.enqueue(
                route.businessId(),
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS,
                MetaWhatsAppJobKeys.audio(message.messageId()),
                payload.toString(),
                properties.getAudioMaxAttempts(),
                Instant.now());
    }

    private static void requireRoute(MetaWhatsAppTenantRoute route) {
        if (route == null || route.businessId() == null || route.phoneNumberId() == null) {
            throw new IllegalArgumentException("Meta WhatsApp tenant route is incomplete");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
