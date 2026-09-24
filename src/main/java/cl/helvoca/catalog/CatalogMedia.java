package cl.helvoca.catalog;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_media",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_catalog_media_item_url",
                columnNames = {"business_id", "catalog_item_id", "media_url"}))
public class CatalogMedia {
    public enum Type { IMAGE, VIDEO, DOCUMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private Type mediaType;

    @Column(name = "media_url", nullable = false, length = 1200)
    private String mediaUrl;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(length = 1024)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
    public Type getMediaType() { return mediaType; }
    public void setMediaType(Type mediaType) { this.mediaType = mediaType; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
