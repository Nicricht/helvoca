package cl.helvoca.messaging.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class WhatsAppAssistantReplyDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppAssistantReplyDeliveryService.class);

    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;

    public WhatsAppAssistantReplyDeliveryService(
            OutboundMessagingProperties properties,
            MessagingProviderRegistry providers) {
        this.properties = properties;
        this.providers = providers;
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
        if (recipient == null || recipient.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Meta reply recipient and content are required");
        }

        Runnable delivery = () -> deliver(
                businessId,
                messageId,
                inboundMessageId,
                recipient,
                content);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
            return;
        }

        delivery.run();
    }

    private void deliver(
            UUID businessId,
            UUID messageId,
            String inboundMessageId,
            String recipient,
            String content) {
        try {
            MessagingProvider provider = providers.require(
                    MetaWhatsAppMessagingProvider.ID,
                    OutboundMessage.Channel.WHATSAPP);
            MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                    businessId,
                    messageId,
                    OutboundMessage.Channel.WHATSAPP,
                    recipient,
                    content,
                    "META_AI_REPLY:" + safeInboundMessageId(inboundMessageId)));
            if (result == null
                    || result.providerMessageId() == null
                    || result.providerMessageId().isBlank()) {
                throw new IllegalStateException("Provider did not confirm Meta reply message id");
            }
            log.info(
                    "Meta WhatsApp AI reply dispatched business={} inboundMessage={} providerMessagePresent=true",
                    businessId,
                    safeInboundMessageId(inboundMessageId));
        } catch (RuntimeException e) {
            log.warn(
                    "Meta WhatsApp AI reply dispatch failed business={} inboundMessage={} type={}",
                    businessId,
                    safeInboundMessageId(inboundMessageId),
                    e.getClass().getSimpleName());
        }
    }

    private static String safeInboundMessageId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String clean = value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }
}
