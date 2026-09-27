package cl.helvoca.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    List<InventoryMovement> findTop100ByBusinessIdAndCatalogItemIdOrderByCreatedAtDesc(
            UUID businessId, UUID catalogItemId);

    List<InventoryMovement> findTop100ByBusinessIdAndVariantIdOrderByCreatedAtDesc(
            UUID businessId, UUID variantId);
}
