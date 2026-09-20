package cl.helvoca.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessagingMessageRepository extends JpaRepository<MessagingMessage, UUID> {
    Optional<MessagingMessage> findByExternalMessageId(String externalMessageId);
    Optional<MessagingMessage> findByProviderAndProviderMessageId(String provider, String providerMessageId);
    List<MessagingMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
