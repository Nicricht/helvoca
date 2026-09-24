package cl.helvoca.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogMediaRepository extends JpaRepository<CatalogMedia, UUID> {
    List<CatalogMedia> findAllByBusinessIdAndCatalogItemIdOrderBySortOrderAscCreatedAtAsc(UUID businessId, UUID catalogItemId);
    List<CatalogMedia> findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(UUID businessId, UUID catalogItemId);
    Optional<CatalogMedia> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByBusinessIdAndCatalogItemIdAndMediaUrl(UUID businessId, UUID catalogItemId, String mediaUrl);
}
