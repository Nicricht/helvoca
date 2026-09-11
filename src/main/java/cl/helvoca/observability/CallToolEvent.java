package cl.helvoca.observability;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_tool_event")
public class CallToolEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "tool_name", nullable = false, length = 80)
    private String toolName;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "result_code", length = 80)
    private String resultCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
