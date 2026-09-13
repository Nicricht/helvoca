package cl.helvoca.call;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_session")
public class CallSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "phone_number_id")
    private UUID phoneNumberId;

    @Column(name = "telephony_provider", nullable = false, length = 30)
    private String telephonyProvider;

    @Column(name = "ai_provider", length = 30)
    private String aiProvider;

    @Column(name = "provider_call_id", nullable = false, unique = true, length = 100)
    private String providerCallId;

    @Column(name = "caller_number", length = 30)
    private String callerNumber;

    @Column(name = "destination_number", nullable = false, length = 30)
    private String destinationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CallStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(length = 80)
    private String resolution;

    @Column(name = "stream_sid", unique = true, length = 100)
    private String streamSid;

    @Column(name = "stream_started_at")
    private Instant streamStartedAt;

    @Column(name = "stream_ended_at")
    private Instant streamEndedAt;

    @Column(name = "ai_setup_completed_at")
    private Instant aiSetupCompletedAt;

    @Column(name = "certification", nullable = false)
    private boolean certification;

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
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(UUID phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getTelephonyProvider() { return telephonyProvider; }
    public void setTelephonyProvider(String telephonyProvider) { this.telephonyProvider = telephonyProvider; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getProviderCallId() { return providerCallId; }
    public void setProviderCallId(String providerCallId) { this.providerCallId = providerCallId; }
    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    public String getDestinationNumber() { return destinationNumber; }
    public void setDestinationNumber(String destinationNumber) { this.destinationNumber = destinationNumber; }
    public CallDirection getDirection() { return direction; }
    public void setDirection(CallDirection direction) { this.direction = direction; }
    public CallStatus getStatus() { return status; }
    public void setStatus(CallStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getStreamSid() { return streamSid; }
    public void setStreamSid(String streamSid) { this.streamSid = streamSid; }
    public Instant getStreamStartedAt() { return streamStartedAt; }
    public void setStreamStartedAt(Instant streamStartedAt) { this.streamStartedAt = streamStartedAt; }
    public Instant getStreamEndedAt() { return streamEndedAt; }
    public void setStreamEndedAt(Instant streamEndedAt) { this.streamEndedAt = streamEndedAt; }
    public Instant getAiSetupCompletedAt() { return aiSetupCompletedAt; }
    public void setAiSetupCompletedAt(Instant aiSetupCompletedAt) { this.aiSetupCompletedAt = aiSetupCompletedAt; }
    public boolean isCertification() { return certification; }
    public void setCertification(boolean certification) { this.certification = certification; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
