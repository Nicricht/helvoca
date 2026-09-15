package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessAutomationPolicyRepository
        extends JpaRepository<BusinessAutomationPolicy, BusinessAutomationPolicy.Key> {

    Optional<BusinessAutomationPolicy> findByBusinessIdAndOperationType(
            UUID businessId,
            BusinessOperation.Type operationType);

    List<BusinessAutomationPolicy> findAllByBusinessIdOrderByOperationTypeAsc(UUID businessId);
}
