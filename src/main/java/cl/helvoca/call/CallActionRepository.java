package cl.helvoca.call;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallActionRepository extends JpaRepository<CallAction, UUID> {
    List<CallAction> findAllByCallIdOrderByCreatedAtAsc(UUID callId);

    Optional<CallAction> findFirstByBusinessIdAndEntityTypeAndEntityIdAndSuccessTrueOrderByCreatedAtAsc(
            UUID businessId, String entityType, UUID entityId);
}
