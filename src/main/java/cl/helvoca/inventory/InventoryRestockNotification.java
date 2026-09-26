package cl.helvoca.inventory;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_restock_notification")
public class InventoryRestockNotification {
    public enum Status { PENDING, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private InventoryRestockSubscription.PreferredChannel preferredChannel;

    @Column(nullable = false, length = 180)
    private String contact;

    @Column(name = "subject_name", nullable = false, length = 220)
    private String subjectName;

    @Column(length = 80)
    private String sku;

    @Column(nullable = false)
    private int available;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "idempotency_key", nullable = false, length = 220)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.PENDING;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public InventoryRestockSubscription.PreferredChannel getPreferredChannel() { return preferredChannel; }
    public void setPreferredChannel(InventoryRestockSubscription.PreferredChannel preferredChannel) { this.preferredChannel = preferredChannel; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getAvailable() { return available; }
    public void setAvailable(int available) { this.available = available; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
