package cl.helvoca.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {
    List<InventoryStock> findAllByBusinessIdOrderByUpdatedAtDesc(UUID businessId);

    Optional<InventoryStock> findByBusinessIdAndCatalogItemId(UUID businessId, UUID catalogItemId);

    Optional<InventoryStock> findByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.businessId = :businessId and s.catalogItemId = :catalogItemId")
    Optional<InventoryStock> lockByBusinessAndCatalogItem(@Param("businessId") UUID businessId,
                                                         @Param("catalogItemId") UUID catalogItemId);
}
