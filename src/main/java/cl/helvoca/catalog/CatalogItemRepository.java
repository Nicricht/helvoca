package cl.helvoca.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {
    List<CatalogItem> findAllByBusinessIdOrderByNameAsc(UUID businessId);
    List<CatalogItem> findAllByBusinessIdAndActiveTrueOrderByNameAsc(UUID businessId);
    Optional<CatalogItem> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByBusinessIdAndKindAndNameIgnoreCase(UUID businessId, CatalogItem.Kind kind, String name);
    boolean existsByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);
}
