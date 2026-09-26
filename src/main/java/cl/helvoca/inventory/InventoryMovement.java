package cl.helvoca.inventory;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {
    public enum Type { CONFIGURE, ADJUSTMENT, RESERVATION, RELEASE, CONSUMPTION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "reserved_delta", nullable = false)
    private int reservedDelta;

    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public int getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(int quantityDelta) { this.quantityDelta = quantityDelta; }
    public int getReservedDelta() { return reservedDelta; }
    public void setReservedDelta(int reservedDelta) { this.reservedDelta = reservedDelta; }
    public int getOnHandAfter() { return onHandAfter; }
    public void setOnHandAfter(int onHandAfter) { this.onHandAfter = onHandAfter; }
    public int getReservedAfter() { return reservedAfter; }
    public void setReservedAfter(int reservedAfter) { this.reservedAfter = reservedAfter; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
}
