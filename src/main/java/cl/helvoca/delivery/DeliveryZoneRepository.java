package cl.helvoca.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {
    List<DeliveryZone> findAllByBusinessIdOrderByNameAsc(UUID businessId);
    List<DeliveryZone> findAllByBusinessIdAndActiveTrueOrderByNameAsc(UUID businessId);
    Optional<DeliveryZone> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
}
