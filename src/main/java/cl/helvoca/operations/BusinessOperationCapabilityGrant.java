package cl.helvoca.operations;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_operation_capability",
        uniqueConstraints = @UniqueConstraint(name = "uq_business_operation_capability",
                columnNames = {"business_id", "capability"}))
public class BusinessOperationCapabilityGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessOperationCapability capability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public BusinessOperationCapability getCapability() { return capability; }
    public void setCapability(BusinessOperationCapability capability) { this.capability = capability; }
    public Instant getCreatedAt() { return createdAt; }
}
