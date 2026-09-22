package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class MetaWhatsAppPendingReplyReplayRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppPendingReplyReplayRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final MessagingConversationRepository conversations;
    private final MessagingMessageRepository messages;
    private final MessagingProviderRegistry providers;

    public MetaWhatsAppPendingReplyReplayRunner(
            @Value("${HELVOCA_META_WHATSAPP_REPLAY_PENDING_REPLY_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_META_WHATSAPP_REPLAY_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            MessagingConversationRepository conversations,
            MessagingMessageRepository messages,
            MessagingProviderRegistry providers) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.conversations = conversations;
        this.messages = messages;
        this.providers = providers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("META_WHATSAPP_PENDING_REPLY_REPLAY skipped=invalid_business_id");
            return;
        }

        databaseContext.runAsTenant(tenantId, () -> replayLatestPending(tenantId));
    }

    private void replayLatestPending(UUID businessId) {
        Pending pending = conversations
                .findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp")
                .stream()
                .limit(25)
                .flatMap(conversation -> pendingForConversation(conversation).stream())
                .max(Comparator.comparing(p -> p.message().getCreatedAt()))
                .orElse(null);

        if (pending == null) {
            log.info("META_WHATSAPP_PENDING_REPLY_REPLAY pending=false");
            return;
        }

        try {
            MessagingProvider provider = providers.require(
                    MetaWhatsAppMessagingProvider.ID,
                    OutboundMessage.Channel.WHATSAPP);
            MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                    businessId,
                    pending.message().getId(),
                    OutboundMessage.Channel.WHATSAPP,
                    pending.conversation().getSender(),
                    pending.message().getReplyText(),
                    "meta-replay:" + pending.message().getId()));

            if (result == null || result.providerMessageId() == null || result.providerMessageId().isBlank()) {
                log.warn("META_WHATSAPP_PENDING_REPLY_REPLAY success=false reason=no_provider_message_id");
                return;
            }

            Instant now = Instant.now();
            MessagingMessage message = pending.message();
            message.setProvider(MetaWhatsAppMessagingProvider.ID);
            message.setProviderMessageId(result.providerMessageId().trim());
            message.setProviderDeliveryStatus("SENT");
            message.setFailureCode(null);
            message.setSentAt(now);
            message.setDeliveryUpdatedAt(now);
            messages.saveAndFlush(message);

            log.info("META_WHATSAPP_PENDING_REPLY_REPLAY success=true");
        } catch (RuntimeException e) {
            log.warn(
                    "META_WHATSAPP_PENDING_REPLY_REPLAY success=false type={} detail={}",
                    e.getClass().getSimpleName(),
                    safeMessage(e.getMessage()));
        }
    }

    private List<Pending> pendingForConversation(MessagingConversation conversation) {
        List<MessagingMessage> all = messages.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId());
        for (int i = all.size() - 1; i >= 0; i--) {
            MessagingMessage message = all.get(i);
            if (!"INBOUND".equalsIgnoreCase(message.getDirection())) continue;
            if (message.getReplyText() == null || message.getReplyText().isBlank()) continue;
            if (message.getProviderMessageId() != null && !message.getProviderMessageId().isBlank()) continue;
            return List.of(new Pending(conversation, message));
        }
        return List.of();
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "unspecified";
        String safe = value.replaceAll("[^A-Za-z0-9._:= -]", "_").trim();
        return safe.length() <= 220 ? safe : safe.substring(0, 220);
    }

    private record Pending(MessagingConversation conversation, MessagingMessage message) {}
}
