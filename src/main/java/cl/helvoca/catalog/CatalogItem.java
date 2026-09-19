package cl.helvoca.catalog;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_catalog_item_business_kind_name",
                columnNames = {"business_id", "kind", "name"}))
public class CatalogItem {
    public enum Kind { SERVICE, PRODUCT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.PRODUCT;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 80)
    private String sku;

    @Column(name = "inventory_tracked", nullable = false)
    private boolean inventoryTracked = false;

    @Column(columnDefinition = "text")
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "CLP";

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "legacy_service_id")
    private UUID legacyServiceId;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public boolean isInventoryTracked() { return inventoryTracked; }
    public void setInventoryTracked(boolean inventoryTracked) { this.inventoryTracked = inventoryTracked; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public UUID getLegacyServiceId() { return legacyServiceId; }
    public void setLegacyServiceId(UUID legacyServiceId) { this.legacyServiceId = legacyServiceId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
