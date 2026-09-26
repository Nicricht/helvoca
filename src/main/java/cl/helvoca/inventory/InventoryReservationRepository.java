package cl.helvoca.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.id = :id and r.businessId = :businessId")
    Optional<InventoryReservation> lockByIdAndBusinessId(@Param("id") UUID id,
                                                         @Param("businessId") UUID businessId);

    List<InventoryReservation> findAllByBusinessIdAndStatusAndExpiresAtBefore(
            UUID businessId, InventoryReservation.Status status, Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from InventoryReservation r
            where r.businessId = :businessId
              and r.referenceType = :referenceType
              and r.referenceId = :referenceId
              and r.status = :status
            order by r.createdAt asc
            """)
    List<InventoryReservation> lockAllByBusinessAndReferenceAndStatus(
            @Param("businessId") UUID businessId,
            @Param("referenceType") String referenceType,
            @Param("referenceId") UUID referenceId,
            @Param("status") InventoryReservation.Status status);

    List<InventoryReservation> findAllByBusinessIdAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtAsc(
            UUID businessId,
            String referenceType,
            UUID referenceId,
            InventoryReservation.Status status);

    List<InventoryReservation> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            InventoryReservation.Status status, Instant expiresAt);
}
