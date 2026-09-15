package cl.helvoca.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByBusinessIdAndProviderAndEventId(
            UUID businessId, String provider, String eventId);

    @Modifying
    @Query(value = """
            INSERT INTO payment_webhook_event
                (id, business_id, provider, event_id, external_id, status, payload_hash, received_at)
            VALUES
                (:id, :businessId, :provider, :eventId, :externalId, 'RECEIVED', :payloadHash, NOW())
            ON CONFLICT (business_id, provider, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id,
              @Param("businessId") UUID businessId,
              @Param("provider") String provider,
              @Param("eventId") String eventId,
              @Param("externalId") String externalId,
              @Param("payloadHash") String payloadHash);
}
