package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.meta.MetaWhatsAppApiException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MetaWhatsAppAssistantReplyJobHandler implements PersistentJobHandler {
    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;
    private final MessagingMessageRepository messages;
    private final MessagingConversationRepository conversations;

    public MetaWhatsAppAssistantReplyJobHandler(
            OutboundMessagingProperties properties,
            MessagingProviderRegistry providers,
            MessagingMessageRepository messages,
            MessagingConversationRepository conversations) {
        this.properties = properties;
        this.providers = providers;
        this.messages = messages;
        this.conversations = conversations;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.META_WHATSAPP_AI_REPLY;
    }

    @Override
    public void handle(PersistentJob job) {
        if (!properties.isDeliveryEnabled()) {
            throw new PermanentJobException("Outbound delivery is disabled");
        }

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

        if (MetaWhatsAppMessagingProvider.ID.equals(inbound.getProvider())
                && inbound.getProviderMessageId() != null
                && !inbound.getProviderMessageId().isBlank()
                && isAccepted(inbound.getProviderDeliveryStatus())) {
            return;
        }

        MessagingConversation conversation = conversations
                .findByIdAndBusinessId(inbound.getConversationId(), job.businessId())
                .orElseThrow(() -> new PermanentJobException("Meta AI reply conversation was not found for tenant"));
        if (conversation.getSender() == null || conversation.getSender().isBlank()) {
            throw new PermanentJobException("Meta AI reply conversation has no recipient");
        }

        MessagingProvider provider;
        try {
            provider = providers.require(
                    MetaWhatsAppMessagingProvider.ID,
                    OutboundMessage.Channel.WHATSAPP);
        } catch (RuntimeException e) {
            throw new PermanentJobException("Meta WhatsApp provider is not available", e);
        }

        try {
            MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                    job.businessId(),
                    messageId,
                    OutboundMessage.Channel.WHATSAPP,
                    conversation.getSender(),
                    inbound.getReplyText(),
                    job.idempotencyKey()));
            if (result == null
                    || result.providerMessageId() == null
                    || result.providerMessageId().isBlank()) {
                throw new RetryableJobException("Meta WhatsApp did not confirm the AI reply message id");
            }

            Instant now = Instant.now();
            inbound.setProvider(MetaWhatsAppMessagingProvider.ID);
            inbound.setProviderMessageId(result.providerMessageId().trim());
            inbound.setProviderDeliveryStatus("SENT");
            inbound.setFailureCode(null);
            inbound.setSentAt(now);
            inbound.setDeliveryUpdatedAt(now);
            messages.saveAndFlush(inbound);
        } catch (RetryableJobException e) {
            throw e;
        } catch (MetaWhatsAppApiException e) {
            if (e.retryable()) {
                throw new RetryableJobException(e.getMessage(), e);
            }
            throw new PermanentJobException(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new PermanentJobException("Meta WhatsApp AI reply is invalid", e);
        } catch (RuntimeException e) {
            throw new RetryableJobException("Meta WhatsApp AI reply dispatch failed", e);
        }
    }

    private static boolean isAccepted(String status) {
        return "SENT".equals(status) || "DELIVERED".equals(status) || "READ".equals(status);
    }

    private static UUID messageId(PersistentJob job) {
        try {
            return UUID.fromString(new JSONObject(job.payloadJson()).getString("messageId"));
        } catch (Exception e) {
            throw new PermanentJobException("Meta AI reply durable job payload is invalid", e);
        }
    }
}
