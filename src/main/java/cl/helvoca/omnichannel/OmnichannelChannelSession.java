package cl.helvoca.omnichannel;

import cl.helvoca.operations.BusinessOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "omnichannel_channel_session",
        uniqueConstraints = @UniqueConstraint(name = "uq_omnichannel_channel_source",
                columnNames = {"business_id", "channel", "source_reference_id"}))
public class OmnichannelChannelSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "omnichannel_session_id", nullable = false)
    private UUID omnichannelSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessOrder.Source channel;

    @Column(name = "source_reference_id", nullable = false)
    private UUID sourceReferenceId;

    @Column(name = "normalized_address", length = 180)
    private String normalizedAddress;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (linkedAt == null) linkedAt = now;
        if (lastActivityAt == null) lastActivityAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getOmnichannelSessionId() { return omnichannelSessionId; }
    public void setOmnichannelSessionId(UUID omnichannelSessionId) { this.omnichannelSessionId = omnichannelSessionId; }
    public BusinessOrder.Source getChannel() { return channel; }
    public void setChannel(BusinessOrder.Source channel) { this.channel = channel; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public void setSourceReferenceId(UUID sourceReferenceId) { this.sourceReferenceId = sourceReferenceId; }
    public String getNormalizedAddress() { return normalizedAddress; }
    public void setNormalizedAddress(String normalizedAddress) { this.normalizedAddress = normalizedAddress; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
