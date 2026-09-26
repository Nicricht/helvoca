package cl.helvoca.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessPaymentRepository extends JpaRepository<BusinessPayment, UUID> {
    Optional<BusinessPayment> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<BusinessPayment> findByOperationIdAndBusinessId(UUID operationId, UUID businessId);
    long countByBusinessIdAndOperationId(UUID businessId, UUID operationId);
    long countByBusinessIdAndTargetOperationId(UUID businessId, UUID targetOperationId);
    Optional<BusinessPayment> findByBusinessIdAndProviderIgnoreCaseAndExternalId(
            UUID businessId, String provider, String externalId);
    List<BusinessPayment> findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
            UUID businessId, UUID targetOperationId);
    List<BusinessPayment> findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(
            UUID businessId, UUID customerId);
    List<BusinessPayment> findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(
            UUID businessId, String contactPhone);
    List<BusinessPayment> findTop5ByBusinessIdAndSourceReferenceIdOrderByCreatedAtDesc(
            UUID businessId, UUID sourceReferenceId);
    List<BusinessPayment> findTop50ByBusinessIdAndStatusInOrderByUpdatedAtAsc(
            UUID businessId, List<BusinessPayment.Status> statuses);
}
