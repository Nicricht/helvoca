package cl.helvoca.servicecatalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {
    List<ServiceItem> findAllByBusinessIdOrderByNameAsc(UUID businessId);
    Optional<ServiceItem> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
