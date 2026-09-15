package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessOperationRepository extends JpaRepository<BusinessOperation, UUID> {
    Optional<BusinessOperation> findByIdAndBusinessId(UUID id, UUID businessId);
}
