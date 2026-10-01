package cl.helvoca.platform;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "demo_session")
public class DemoSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId;

    @Column(name = "demo_profile_id", nullable = false)
    private UUID demoProfileId;

    @Column(name = "runtime_business_id", nullable = false)
    private UUID runtimeBusinessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemoSessionState state;

    @Column(name = "configuration_revision", nullable = false, length = 120)
    private String configurationRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "readiness_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> readinessSnapshot = new LinkedHashMap<>();

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "staged_at")
    private Instant stagedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DemoSession preparing(UUID profileId, UUID runtimeBusinessId, String revision) {
        DemoSession value = new DemoSession();
        value.correlationId = UUID.randomUUID();
        value.demoProfileId = profileId;
        value.runtimeBusinessId = runtimeBusinessId;
        value.configurationRevision = revision;
        value.state = DemoSessionState.PREPARING;
        return value;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markStaged() {
        stagedAt = Instant.now();
    }

    public void markReady() {
        state = DemoSessionState.READY;
        failureReason = null;
    }

    public void markActive() {
        state = DemoSessionState.ACTIVE;
        if (startedAt == null) startedAt = Instant.now();
    }

    public void markFinished() {
        state = DemoSessionState.FINISHED;
        finishedAt = Instant.now();
    }

    public void markFailed(String reason) {
        state = DemoSessionState.FAILED;
        failureReason = reason == null || reason.isBlank() ? "Demo preparation failed" : reason;
    }

    public UUID getId() { return id; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getDemoProfileId() { return demoProfileId; }
    public UUID getRuntimeBusinessId() { return runtimeBusinessId; }
    public DemoSessionState getState() { return state; }
    public String getConfigurationRevision() { return configurationRevision; }
    public Map<String, Object> getReadinessSnapshot() { return readinessSnapshot; }
    public void setReadinessSnapshot(Map<String, Object> readinessSnapshot) {
        this.readinessSnapshot = new LinkedHashMap<>(readinessSnapshot == null ? Map.of() : readinessSnapshot);
    }
    public String getFailureReason() { return failureReason; }
    public Instant getStagedAt() { return stagedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
