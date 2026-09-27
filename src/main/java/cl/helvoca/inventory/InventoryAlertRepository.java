package cl.helvoca.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertRepository extends JpaRepository<InventoryAlert, UUID> {
    List<InventoryAlert> findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
            UUID businessId, InventoryAlert.Status status);

    List<InventoryAlert> findTop100ByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<InventoryAlert> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from InventoryAlert a
            where a.businessId = :businessId
              and a.catalogItemId = :catalogItemId
              and a.variantId is null
              and a.status = cl.helvoca.inventory.InventoryAlert.Status.OPEN
            order by a.createdAt desc
            """)
    Optional<InventoryAlert> lockOpenBase(
            @Param("businessId") UUID businessId,
            @Param("catalogItemId") UUID catalogItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from InventoryAlert a
            where a.businessId = :businessId
              and a.catalogItemId = :catalogItemId
              and a.variantId = :variantId
              and a.status = cl.helvoca.inventory.InventoryAlert.Status.OPEN
            order by a.createdAt desc
            """)
    Optional<InventoryAlert> lockOpenVariant(
            @Param("businessId") UUID businessId,
            @Param("catalogItemId") UUID catalogItemId,
            @Param("variantId") UUID variantId);
}
