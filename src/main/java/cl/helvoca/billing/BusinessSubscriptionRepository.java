package cl.helvoca.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessSubscriptionRepository extends JpaRepository<BusinessSubscription, UUID> {
    Optional<BusinessSubscription> findByBusinessId(UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BusinessSubscription> findByExternalSubscriptionId(String externalSubscriptionId);

    @Query("""
            select s from BusinessSubscription s
             where s.billingProvider = :provider
               and s.externalSubscriptionId is not null
               and trim(s.externalSubscriptionId) <> ''
               and s.status in :statuses
             order by s.updatedAt asc
            """)
    List<BusinessSubscription> findInvoiceRecoveryCandidates(
            @Param("provider") String provider,
            @Param("statuses") Collection<SubscriptionStatus> statuses,
            Pageable pageable);
}
