package cl.helvoca.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessagingConversationRepository extends JpaRepository<MessagingConversation, UUID> {
    Optional<MessagingConversation> findFirstByBusinessIdAndChannelAndSenderAndRecipientAndLastMessageAtAfterOrderByLastMessageAtDesc(
            UUID businessId, String channel, String sender, String recipient, Instant after);
}
