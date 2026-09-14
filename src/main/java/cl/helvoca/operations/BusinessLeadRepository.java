package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessLeadRepository extends JpaRepository<BusinessLead, UUID> {
    Optional<BusinessLead> findByIdAndBusinessId(UUID id, UUID businessId);
    List<BusinessLead> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
