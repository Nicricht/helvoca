package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessOrderRepository extends JpaRepository<BusinessOrder, UUID> {
    Optional<BusinessOrder> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<BusinessOrder> findByOperationIdAndBusinessId(UUID operationId, UUID businessId);
    List<BusinessOrder> findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(UUID businessId, UUID customerId);
    List<BusinessOrder> findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(UUID businessId, String contactPhone);
    List<BusinessOrder> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    List<BusinessOrder> findAllByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID businessId, Instant start, Instant end);
}
