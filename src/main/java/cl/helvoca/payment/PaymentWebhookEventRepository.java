package cl.helvoca.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByBusinessIdAndProviderAndEventId(
            UUID businessId, String provider, String eventId);
}
