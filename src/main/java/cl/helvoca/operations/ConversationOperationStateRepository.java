package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationOperationStateRepository extends JpaRepository<ConversationOperationState, UUID> {
    Optional<ConversationOperationState> findByBusinessIdAndChannelAndSourceReferenceId(
            UUID businessId,
            BusinessOrder.Source channel,
            UUID sourceReferenceId);

    Optional<ConversationOperationState> findFirstByBusinessIdAndOmnichannelSessionIdOrderByUpdatedAtDesc(
            UUID businessId,
            UUID omnichannelSessionId);
}
