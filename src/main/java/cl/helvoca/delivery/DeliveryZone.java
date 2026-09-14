package cl.helvoca.delivery;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_zone",
        uniqueConstraints = @UniqueConstraint(name = "uq_delivery_zone_business_name",
                columnNames = {"business_id", "name"}))
public class DeliveryZone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "coverage_terms", nullable = false, columnDefinition = "text")
    private String coverageTerms = "";

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "minimum_order", precision = 12, scale = 2)
    private BigDecimal minimumOrder;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCoverageTerms() { return coverageTerms; }
    public void setCoverageTerms(String coverageTerms) { this.coverageTerms = coverageTerms; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public BigDecimal getMinimumOrder() { return minimumOrder; }
    public void setMinimumOrder(BigDecimal minimumOrder) { this.minimumOrder = minimumOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
