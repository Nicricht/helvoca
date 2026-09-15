package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OperationConfirmationRepository extends JpaRepository<OperationConfirmation, UUID> {
    Optional<OperationConfirmation> findByBusinessIdAndOperationIdAndOperationRevision(
            UUID businessId, UUID operationId, Integer operationRevision);
    Optional<OperationConfirmation> findByBusinessIdAndToken(UUID businessId, UUID token);
    Optional<OperationConfirmation> findFirstByBusinessIdAndOperationIdOrderByOperationRevisionDesc(
            UUID businessId, UUID operationId);
}
