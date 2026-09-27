package cl.helvoca.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRestockNotificationRepository extends JpaRepository<InventoryRestockNotification, UUID> {
    List<InventoryRestockNotification> findTop100ByBusinessIdAndStatusOrderByCreatedAtDesc(
            UUID businessId, InventoryRestockNotification.Status status);

    Optional<InventoryRestockNotification> findByBusinessIdAndSubscriptionId(
            UUID businessId, UUID subscriptionId);
}
