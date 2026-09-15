package cl.helvoca.omnichannel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, UUID> {
    List<CustomerIdentity> findAllByBusinessIdAndIdentityTypeAndNormalizedValueAndVerificationStatusIn(
            UUID businessId,
            CustomerIdentity.Type identityType,
            String normalizedValue,
            Collection<CustomerIdentity.VerificationStatus> statuses);

    Optional<CustomerIdentity> findByBusinessIdAndCustomerIdAndIdentityTypeAndNormalizedValue(
            UUID businessId,
            UUID customerId,
            CustomerIdentity.Type identityType,
            String normalizedValue);
}
