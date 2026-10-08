package cl.helvoca.request;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRequestRepository extends JpaRepository<BusinessRequest, UUID> {
    List<BusinessRequest> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    List<BusinessRequest> findTop10ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    long countByBusinessIdAndStatusIn(UUID businessId, Collection<RequestStatus> statuses);
    Optional<BusinessRequest> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BusinessRequest r where r.id = :id and r.businessId = :businessId")
    Optional<BusinessRequest> lockByIdAndBusinessId(@Param("id") UUID id,
                                                    @Param("businessId") UUID businessId);
}
