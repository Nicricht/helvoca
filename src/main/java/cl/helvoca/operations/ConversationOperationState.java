package cl.helvoca.operations;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "conversation_operation_state",
        uniqueConstraints = @UniqueConstraint(name = "uq_conversation_operation_state",
                columnNames = {"business_id", "channel", "source_reference_id"}))
public class ConversationOperationState {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "source_reference_id", nullable = false)
    private UUID sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessOrder.Source channel;

    @Column(name = "active_operation_id")
    private UUID activeOperationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> state = new LinkedHashMap<>();

    @Column(nullable = false)
    private Integer revision = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (revision == null || revision < 1) revision = 1;
        if (state == null) state = new LinkedHashMap<>();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public void setSourceReferenceId(UUID sourceReferenceId) { this.sourceReferenceId = sourceReferenceId; }
    public BusinessOrder.Source getChannel() { return channel; }
    public void setChannel(BusinessOrder.Source channel) { this.channel = channel; }
    public UUID getActiveOperationId() { return activeOperationId; }
    public void setActiveOperationId(UUID activeOperationId) { this.activeOperationId = activeOperationId; }
    public Map<String, Object> getState() { return state; }
    public void setState(Map<String, Object> state) { this.state = state; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
