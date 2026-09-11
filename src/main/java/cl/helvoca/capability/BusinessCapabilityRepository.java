package cl.helvoca.capability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessCapabilityRepository extends JpaRepository<BusinessCapability, UUID> {
    List<BusinessCapability> findAllByBusinessIdOrderByCodeAsc(UUID businessId);
    Optional<BusinessCapability> findByBusinessIdAndCode(UUID businessId, BusinessCapabilityCode code);
}
