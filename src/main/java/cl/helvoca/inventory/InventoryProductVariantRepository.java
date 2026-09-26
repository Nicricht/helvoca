package cl.helvoca.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryProductVariantRepository extends JpaRepository<InventoryProductVariant, UUID> {
    List<InventoryProductVariant> findAllByBusinessIdAndCatalogItemIdOrderByNameAsc(
            UUID businessId, UUID catalogItemId);

    Optional<InventoryProductVariant> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<InventoryProductVariant> findByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);

    boolean existsByBusinessIdAndCatalogItemIdAndNameIgnoreCase(
            UUID businessId, UUID catalogItemId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from InventoryProductVariant v
            where v.id = :id and v.businessId = :businessId
            """)
    Optional<InventoryProductVariant> lockByIdAndBusinessId(
            @Param("id") UUID id,
            @Param("businessId") UUID businessId);
}
