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
    @Column(name = "configuration_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configurationSnapshot = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "readiness_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> readinessSnapshot = new LinkedHashMap<>();

    @Column(name = "operator", nullable = false, length = 180)
    private String operator = "platform";

    @Column(name = "external_effects_state", nullable = false, length = 30)
    private String externalEffectsState = "DISARMED";

    @Column(name = "payment_state", nullable = false, length = 30)
    private String paymentState = "SANDBOX_ONLY";

    @Column(name = "converted_pilot_business_id")
    private UUID convertedPilotBusinessId;

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
        return preparing(profileId, runtimeBusinessId, revision, "platform", Map.of());
    }

    public static DemoSession preparing(UUID profileId,
                                        UUID runtimeBusinessId,
                                        String revision,
                                        String operator,
                                        Map<String, Object> configurationSnapshot) {
        DemoSession value = new DemoSession();
        value.correlationId = UUID.randomUUID();
        value.demoProfileId = profileId;
        value.runtimeBusinessId = runtimeBusinessId;
        value.configurationRevision = revision;
        value.operator = operator == null || operator.isBlank() ? "platform" : operator.trim().toLowerCase();
        value.setConfigurationSnapshot(configurationSnapshot);
        value.state = DemoSessionState.PREPARING;
        return value;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (operator == null || operator.isBlank()) operator = "platform";
        if (externalEffectsState == null || externalEffectsState.isBlank()) externalEffectsState = "DISARMED";
        if (paymentState == null || paymentState.isBlank()) paymentState = "SANDBOX_ONLY";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void markStaged() { stagedAt = Instant.now(); }
    public void markReady() { state = DemoSessionState.READY; failureReason = null; }
    public void markActive() {
        state = DemoSessionState.ACTIVE;
        if (startedAt == null) startedAt = Instant.now();
    }
    public void markFinished() {
        state = DemoSessionState.FINISHED;
        finishedAt = Instant.now();
    }
    public void markAborted() {
        state = DemoSessionState.ABORTED;
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
    public Map<String, Object> getConfigurationSnapshot() { return configurationSnapshot; }
    public void setConfigurationSnapshot(Map<String, Object> value) {
        configurationSnapshot = new LinkedHashMap<>(value == null ? Map.of() : value);
    }
    public Map<String, Object> getReadinessSnapshot() { return readinessSnapshot; }
    public void setReadinessSnapshot(Map<String, Object> value) {
        readinessSnapshot = new LinkedHashMap<>(value == null ? Map.of() : value);
    }
    public String getOperator() { return operator; }
    public String getExternalEffectsState() { return externalEffectsState; }
    public String getPaymentState() { return paymentState; }
    public UUID getConvertedPilotBusinessId() { return convertedPilotBusinessId; }
    public void setConvertedPilotBusinessId(UUID convertedPilotBusinessId) {
        this.convertedPilotBusinessId = convertedPilotBusinessId;
    }
    public String getFailureReason() { return failureReason; }
    public Instant getStagedAt() { return stagedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
