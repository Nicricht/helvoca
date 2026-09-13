package cl.helvoca.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessSubscriptionRepository extends JpaRepository<BusinessSubscription, UUID> {
    Optional<BusinessSubscription> findByBusinessId(UUID businessId);
    Optional<BusinessSubscription> findByExternalSubscriptionId(String externalSubscriptionId);
}
