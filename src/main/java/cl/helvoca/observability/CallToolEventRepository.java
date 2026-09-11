package cl.helvoca.observability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CallToolEventRepository extends JpaRepository<CallToolEvent, UUID> {
    List<CallToolEvent> findTop20ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    List<CallToolEvent> findAllByCallIdOrderByCreatedAtAsc(UUID callId);
    long countByBusinessIdAndSuccessFalseAndCreatedAtBetween(UUID businessId, Instant from, Instant to);
}
