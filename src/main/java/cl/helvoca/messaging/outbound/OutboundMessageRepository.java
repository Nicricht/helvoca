package cl.helvoca.messaging.outbound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {
    Optional<OutboundMessage> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<OutboundMessage> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);
    List<OutboundMessage> findTop100ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
