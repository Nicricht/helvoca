package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WhatsAppAssistantReplyDeliveryService {
    private final OutboundMessagingProperties properties;
    private final PersistentJobService jobs;

    public WhatsAppAssistantReplyDeliveryService(
            OutboundMessagingProperties properties,
            PersistentJobService jobs) {
        this.properties = properties;
        this.jobs = jobs;
    }

    public void scheduleMetaReply(
            UUID businessId,
            UUID messageId,
            String inboundMessageId,
            String providerId,
            String recipient,
            String content) {
        if (!properties.isDeliveryEnabled()) {
            return;
        }
        if (!MetaWhatsAppMessagingProvider.ID.equalsIgnoreCase(providerId)) {
            return;
        }
        if (businessId == null || messageId == null) {
            throw new IllegalArgumentException("Meta reply identifiers are required");
        }
        if (inboundMessageId == null || inboundMessageId.isBlank()) {
            throw new IllegalArgumentException("Meta inbound message id is required");
        }
        if (recipient == null || recipient.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Meta reply recipient and content are required");
        }

        String idempotencyKey = "meta-ai-reply:" + safeInboundMessageId(inboundMessageId);
        String payload = new JSONObject()
                .put("messageId", messageId.toString())
                .toString();

        jobs.enqueue(
                businessId,
                null,
                PersistentJob.Type.META_WHATSAPP_AI_REPLY,
                idempotencyKey,
                payload);
    }

    static String safeInboundMessageId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String clean = value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }
}
