package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessOperationCapabilityRepository
        extends JpaRepository<BusinessOperationCapabilityGrant, UUID> {
    List<BusinessOperationCapabilityGrant> findAllByBusinessIdOrderByCapabilityAsc(UUID businessId);
    void deleteAllByBusinessId(UUID businessId);
    boolean existsByBusinessIdAndCapability(UUID businessId, BusinessOperationCapability capability);
}
