package cl.helvoca.platform;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_session")
public class DemoSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "runtime_business_id", nullable = false)
    private UUID runtimeBusinessId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemoSessionState state = DemoSessionState.PREPARING;

    @Column(name = "expected_participant_phone", length = 30)
    private String expectedParticipantPhone;

    @Column(name = "configuration_revision", length = 64)
    private String configurationRevision;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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
    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }
    public UUID getRuntimeBusinessId() { return runtimeBusinessId; }
    public void setRuntimeBusinessId(UUID runtimeBusinessId) { this.runtimeBusinessId = runtimeBusinessId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public DemoSessionState getState() { return state; }
    public void setState(DemoSessionState state) { this.state = state; }
    public String getExpectedParticipantPhone() { return expectedParticipantPhone; }
    public void setExpectedParticipantPhone(String expectedParticipantPhone) { this.expectedParticipantPhone = expectedParticipantPhone; }
    public String getConfigurationRevision() { return configurationRevision; }
    public void setConfigurationRevision(String configurationRevision) { this.configurationRevision = configurationRevision; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPreparedAt() { return preparedAt; }
    public void setPreparedAt(Instant preparedAt) { this.preparedAt = preparedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
