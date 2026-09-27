package cl.helvoca.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface BusinessSubscriptionRepository extends JpaRepository<BusinessSubscription, UUID> {
    Optional<BusinessSubscription> findByBusinessId(UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BusinessSubscription> findByExternalSubscriptionId(String externalSubscriptionId);
}
