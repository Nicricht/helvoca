package cl.helvoca.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentProviderConfigRepository extends JpaRepository<PaymentProviderConfig, UUID> {
    Optional<PaymentProviderConfig> findByWebhookKey(UUID webhookKey);
}
