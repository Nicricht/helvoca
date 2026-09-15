package cl.helvoca.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_payment_provider_config")
public class PaymentProviderConfig {
    public enum Mode { SANDBOX, LIVE }

    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 60)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "credential_ref", nullable = false, length = 80)
    private String credentialRef;

    @Column(name = "webhook_key", nullable = false, unique = true)
    private UUID webhookKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (webhookKey == null) webhookKey = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public UUID getWebhookKey() { return webhookKey; }
    public void setWebhookKey(UUID webhookKey) { this.webhookKey = webhookKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
