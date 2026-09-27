package cl.helvoca.inventory;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_restock_subscription")
public class InventoryRestockSubscription {
    public enum PreferredChannel { WHATSAPP, SMS, EMAIL }
    public enum Status { ACTIVE, NOTIFIED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private PreferredChannel preferredChannel;

    @Column(nullable = false, length = 180)
    private String contact;

    @Column(name = "normalized_contact", nullable = false, length = 180)
    private String normalizedContact;

    @Column(name = "consent_granted", nullable = false)
    private boolean consentGranted;

    @Column(name = "consent_granted_at", nullable = false)
    private Instant consentGrantedAt;

    @Column(name = "consent_source", nullable = false, length = 40)
    private String consentSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (consentGrantedAt == null) consentGrantedAt = now;
        if (status == null) status = Status.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public PreferredChannel getPreferredChannel() { return preferredChannel; }
    public void setPreferredChannel(PreferredChannel preferredChannel) { this.preferredChannel = preferredChannel; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getNormalizedContact() { return normalizedContact; }
    public void setNormalizedContact(String normalizedContact) { this.normalizedContact = normalizedContact; }
    public boolean isConsentGranted() { return consentGranted; }
    public void setConsentGranted(boolean consentGranted) { this.consentGranted = consentGranted; }
    public Instant getConsentGrantedAt() { return consentGrantedAt; }
    public void setConsentGrantedAt(Instant consentGrantedAt) { this.consentGrantedAt = consentGrantedAt; }
    public String getConsentSource() { return consentSource; }
    public void setConsentSource(String consentSource) { this.consentSource = consentSource; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(Instant notifiedAt) { this.notifiedAt = notifiedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
