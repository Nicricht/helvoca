package cl.helvoca.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRequestRepository extends JpaRepository<BusinessRequest, UUID> {
    List<BusinessRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    List<BusinessRequest> findTop10ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<BusinessRequest> findByIdAndBusinessId(UUID id, UUID businessId);
    long countByBusinessIdAndStatus(UUID businessId, BusinessRequestStatus status);
    long countByBusinessIdAndCreatedAtBetween(UUID businessId, Instant from, Instant to);
}
