package cl.helvoca.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRestockSubscriptionRepository extends JpaRepository<InventoryRestockSubscription, UUID> {
    List<InventoryRestockSubscription> findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
            UUID businessId, InventoryRestockSubscription.Status status);

    Optional<InventoryRestockSubscription> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from InventoryRestockSubscription s
            where s.id = :id
              and s.businessId = :businessId
            """)
    Optional<InventoryRestockSubscription> lockByIdAndBusinessId(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from InventoryRestockSubscription s
            where s.businessId = :businessId
              and s.catalogItemId = :catalogItemId
              and ((:variantId is null and s.variantId is null) or s.variantId = :variantId)
              and s.preferredChannel = :channel
              and s.normalizedContact = :normalizedContact
              and s.status = cl.helvoca.inventory.InventoryRestockSubscription.Status.ACTIVE
            """)
    Optional<InventoryRestockSubscription> lockActiveDuplicate(
            @Param("businessId") UUID businessId,
            @Param("catalogItemId") UUID catalogItemId,
            @Param("variantId") UUID variantId,
            @Param("channel") InventoryRestockSubscription.PreferredChannel channel,
            @Param("normalizedContact") String normalizedContact);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from InventoryRestockSubscription s
            where s.businessId = :businessId
              and s.catalogItemId = :catalogItemId
              and ((:variantId is null and s.variantId is null) or s.variantId = :variantId)
              and s.status = cl.helvoca.inventory.InventoryRestockSubscription.Status.ACTIVE
            order by s.createdAt asc
            """)
    List<InventoryRestockSubscription> lockActiveForSubject(
            @Param("businessId") UUID businessId,
            @Param("catalogItemId") UUID catalogItemId,
            @Param("variantId") UUID variantId);
}
