package cl.helvoca.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessDeliveryRepository extends JpaRepository<BusinessDelivery, UUID> {
    Optional<BusinessDelivery> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<BusinessDelivery> findByOperationIdAndBusinessId(UUID operationId, UUID businessId);
    List<BusinessDelivery> findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(UUID businessId, UUID customerId);
    List<BusinessDelivery> findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(UUID businessId, String contactPhone);
    List<BusinessDelivery> findTop5ByBusinessIdAndSourceReferenceIdOrderByCreatedAtDesc(UUID businessId, UUID sourceReferenceId);
    List<BusinessDelivery> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
