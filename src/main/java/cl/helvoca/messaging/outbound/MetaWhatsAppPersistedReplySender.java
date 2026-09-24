package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.meta.MetaWhatsAppApiException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MetaWhatsAppPersistedReplySender {
    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;
    private final MessagingMessageRepository messages;
    private final MessagingConversationRepository conversations;

    public MetaWhatsAppPersistedReplySender(
            OutboundMessagingProperties properties,
            MessagingProviderRegistry providers,
            MessagingMessageRepository messages,
            MessagingConversationRepository conversations) {
        this.properties = properties;
        this.providers = providers;
        this.messages = messages;
        this.conversations = conversations;
    }

    public void send(PersistentJob job, MessagingMessage inbound) {
        if (!properties.isDeliveryEnabled()) {
            throw new PersistentJobHandler.PermanentJobException("Outbound delivery is disabled");
        }
        if (job == null || inbound == null) {
            throw new PersistentJobHandler.PermanentJobException("Meta persisted reply source is required");
        }
        if (!"INBOUND".equalsIgnoreCase(inbound.getDirection())) {
            throw new PersistentJobHandler.PermanentJobException("Meta persisted reply source must be inbound");
        }
        if (inbound.getReplyText() == null || inbound.getReplyText().isBlank()) {
            throw new PersistentJobHandler.PermanentJobException("Meta persisted reply source has no persisted reply");
        }

        if (MetaWhatsAppMessagingProvider.ID.equals(inbound.getProvider())
                && inbound.getProviderMessageId() != null
                && !inbound.getProviderMessageId().isBlank()
                && isAccepted(inbound.getProviderDeliveryStatus())) {
            return;
        }

        MessagingConversation conversation = conversations
                .findByIdAndBusinessId(inbound.getConversationId(), job.businessId())
                .orElseThrow(() -> new PersistentJobHandler.PermanentJobException(
                        "Meta persisted reply conversation was not found for tenant"));
        if (conversation.getSender() == null || conversation.getSender().isBlank()) {
            throw new PersistentJobHandler.PermanentJobException(
                    "Meta persisted reply conversation has no recipient");
        }

        MessagingProvider provider;
        try {
            provider = providers.require(
                    MetaWhatsAppMessagingProvider.ID,
                    OutboundMessage.Channel.WHATSAPP);
        } catch (RuntimeException failure) {
            throw new PersistentJobHandler.PermanentJobException(
                    "Meta WhatsApp provider is not available", failure);
        }

        try {
            MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                    job.businessId(),
                    inbound.getId(),
                    OutboundMessage.Channel.WHATSAPP,
                    conversation.getSender(),
                    inbound.getReplyText(),
                    job.idempotencyKey()));
            if (result == null
                    || result.providerMessageId() == null
                    || result.providerMessageId().isBlank()) {
                throw new PersistentJobHandler.RetryableJobException(
                        "Meta WhatsApp did not confirm the persisted reply message id");
            }

            Instant now = Instant.now();
            inbound.setProvider(MetaWhatsAppMessagingProvider.ID);
            inbound.setProviderMessageId(result.providerMessageId().trim());
            inbound.setProviderDeliveryStatus("SENT");
            inbound.setSentAt(now);
            inbound.setDeliveryUpdatedAt(now);
            messages.saveAndFlush(inbound);
        } catch (PersistentJobHandler.RetryableJobException failure) {
            throw failure;
        } catch (MetaWhatsAppApiException failure) {
            if (failure.retryable()) {
                throw new PersistentJobHandler.RetryableJobException(failure.getMessage(), failure);
            }
            throw new PersistentJobHandler.PermanentJobException(failure.getMessage(), failure);
        } catch (IllegalArgumentException failure) {
            throw new PersistentJobHandler.PermanentJobException(
                    "Meta WhatsApp persisted reply is invalid", failure);
        } catch (RuntimeException failure) {
            throw new PersistentJobHandler.RetryableJobException(
                    "Meta WhatsApp persisted reply dispatch failed", failure);
        }
    }

    private static boolean isAccepted(String status) {
        return "SENT".equals(status) || "DELIVERED".equals(status) || "READ".equals(status);
    }
}
