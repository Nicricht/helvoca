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

    List<InventoryReservation> findAllByBusinessIdAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtAsc(
            UUID businessId,
            String referenceType,
            UUID referenceId,
            InventoryReservation.Status status);
}
