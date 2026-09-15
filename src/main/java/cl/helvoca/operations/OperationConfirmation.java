package cl.helvoca.operations;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operation_confirmation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_operation_confirmation_revision",
                        columnNames = {"business_id", "operation_id", "operation_revision"}),
                @UniqueConstraint(name = "uq_operation_confirmation_token",
                        columnNames = {"business_id", "token"})
        })
public class OperationConfirmation {
    public enum State { AWAITING, CONSUMED, INVALIDATED, EXPIRED, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "operation_id", nullable = false) private UUID operationId;
    @Column(name = "operation_revision", nullable = false) private Integer operationRevision;
    @Column(nullable = false) private UUID token;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state = State.AWAITING;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Enumerated(EnumType.STRING) @Column(name = "resolved_channel", length = 20) private BusinessOrder.Source resolvedChannel;
    @Column(name = "resolved_source_reference_id") private UUID resolvedSourceReferenceId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (issuedAt == null) issuedAt = now;
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public Integer getOperationRevision() { return operationRevision; }
    public void setOperationRevision(Integer operationRevision) { this.operationRevision = operationRevision; }
    public UUID getToken() { return token; }
    public void setToken(UUID token) { this.token = token; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public BusinessOrder.Source getResolvedChannel() { return resolvedChannel; }
    public void setResolvedChannel(BusinessOrder.Source resolvedChannel) { this.resolvedChannel = resolvedChannel; }
    public UUID getResolvedSourceReferenceId() { return resolvedSourceReferenceId; }
    public void setResolvedSourceReferenceId(UUID resolvedSourceReferenceId) { this.resolvedSourceReferenceId = resolvedSourceReferenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
