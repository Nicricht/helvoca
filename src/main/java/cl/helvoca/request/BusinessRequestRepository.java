package cl.helvoca.request;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRequestRepository extends JpaRepository<BusinessRequest, UUID> {
    List<BusinessRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    Optional<BusinessRequest> findByIdAndBusinessId(UUID id, UUID businessId);
}
