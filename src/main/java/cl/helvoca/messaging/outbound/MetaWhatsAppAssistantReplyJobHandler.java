package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetaWhatsAppAssistantReplyJobHandler implements PersistentJobHandler {
    private final MessagingMessageRepository messages;
    private final MetaWhatsAppPersistedReplySender sender;

    @Autowired
    public MetaWhatsAppAssistantReplyJobHandler(
            MessagingMessageRepository messages,
            MetaWhatsAppPersistedReplySender sender) {
        this.messages = messages;
        this.sender = sender;
    }

    public MetaWhatsAppAssistantReplyJobHandler(
            OutboundMessagingProperties properties,
            MessagingProviderRegistry providers,
            MessagingMessageRepository messages,
            MessagingConversationRepository conversations) {
        this(messages, new MetaWhatsAppPersistedReplySender(
                properties,
                providers,
                messages,
                conversations));
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.META_WHATSAPP_AI_REPLY;
    }

    @Override
    public void handle(PersistentJob job) {
        UUID messageId = messageId(job);
        MessagingMessage inbound = messages.findById(messageId)
                .orElseThrow(() -> new PermanentJobException("Meta AI reply source message was not found"));
        if (!"INBOUND".equalsIgnoreCase(inbound.getDirection())) {
            throw new PermanentJobException("Meta AI reply source must be an inbound message");
        }
        if (inbound.getExternalMessageId() == null || inbound.getExternalMessageId().isBlank()) {
            throw new PermanentJobException("Meta AI reply source has no external message id");
        }
        if (inbound.getReplyText() == null || inbound.getReplyText().isBlank()) {
            throw new PermanentJobException("Meta AI reply source has no persisted reply");
        }

        String expectedKey = "meta-ai-reply:" +
                WhatsAppAssistantReplyDeliveryService.safeInboundMessageId(inbound.getExternalMessageId());
        if (!expectedKey.equals(job.idempotencyKey())) {
            throw new PermanentJobException("Meta AI reply idempotency key does not match source message");
        }

        sender.send(job, inbound, true);
    }

    private static UUID messageId(PersistentJob job) {
        try {
            return UUID.fromString(new JSONObject(job.payloadJson()).getString("messageId"));
        } catch (Exception failure) {
            throw new PermanentJobException("Meta AI reply durable job payload is invalid", failure);
        }
    }
}
