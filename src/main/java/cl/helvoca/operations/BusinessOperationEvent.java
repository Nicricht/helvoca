package cl.helvoca.operations;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "business_operation_event")
public class BusinessOperationEvent {
    public enum ActorType { SYSTEM, HUMAN, AI, PROVIDER }

    @Id
    private UUID id;

    @Column(name = "sequence_no", nullable = false, insertable = false, updatable = false)
    private Long sequenceNo;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 20)
    private BusinessOperation.Type operationType;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private BusinessOrder.Source channel;

    @Column(name = "source_reference_id", updatable = false)
    private UUID sourceReferenceId;

    @Column(nullable = false, updatable = false)
    private Integer revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private BusinessOperation.Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 30)
    private BusinessOperation.Status previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private ActorType actorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BusinessOperationEvent() { }

    public UUID getId() { return id; }
    public Long getSequenceNo() { return sequenceNo; }
    public UUID getBusinessId() { return businessId; }
    public UUID getOperationId() { return operationId; }
    public BusinessOperation.Type getOperationType() { return operationType; }
    public String getEventType() { return eventType; }
    public BusinessOrder.Source getChannel() { return channel; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public Integer getRevision() { return revision; }
    public BusinessOperation.Status getStatus() { return status; }
    public BusinessOperation.Status getPreviousStatus() { return previousStatus; }
    public ActorType getActorType() { return actorType; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
