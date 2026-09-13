package cl.helvoca.servicecatalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {
    List<ServiceItem> findAllByBusinessIdOrderByNameAsc(UUID businessId);
    Optional<ServiceItem> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceItem s where s.id = :id and s.businessId = :businessId")
    Optional<ServiceItem> findByIdAndBusinessIdForUpdate(@Param("id") UUID id,
                                                         @Param("businessId") UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
