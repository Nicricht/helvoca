package cl.helvoca.messaging;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessagingConversationQueryService {
    private static final String WHATSAPP = "whatsapp";

    private final MessagingConversationRepository conversations;
    private final MessagingMessageRepository messages;
    private final TenantProvider tenantProvider;

    public MessagingConversationQueryService(MessagingConversationRepository conversations,
                                             MessagingMessageRepository messages,
                                             TenantProvider tenantProvider) {
        this.conversations = conversations;
        this.messages = messages;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<ConversationItem> listWhatsApp() {
        UUID businessId = tenantProvider.requireBusinessId();
        return conversations.findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, WHATSAPP)
                .stream().map(MessagingConversationQueryService::toItem).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDetail> whatsappDetail(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        return conversations.findByIdAndBusinessId(id, businessId)
                .filter(conversation -> WHATSAPP.equalsIgnoreCase(conversation.getChannel()))
                .map(conversation -> new ConversationDetail(
                        toItem(conversation),
                        messages.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId())
                                .stream().map(MessagingConversationQueryService::toMessage).toList()));
    }

    private static ConversationItem toItem(MessagingConversation conversation) {
        return new ConversationItem(
                conversation.getId(),
                conversation.getCustomerId(),
                conversation.getChannel(),
                conversation.getSender(),
                conversation.getRecipient(),
                conversation.getOpenedAt(),
                conversation.getLastMessageAt());
    }

    private static MessageItem toMessage(MessagingMessage message) {
        return new MessageItem(
                message.getId(),
                message.getDirection(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }

    public record ConversationItem(UUID id,
                                   UUID customerId,
                                   String channel,
                                   String sender,
                                   String recipient,
                                   Instant openedAt,
                                   Instant lastMessageAt) {}

    public record ConversationDetail(ConversationItem conversation, List<MessageItem> messages) {}

    public record MessageItem(UUID id,
                              String direction,
                              String role,
                              String content,
                              Instant createdAt) {}
}
