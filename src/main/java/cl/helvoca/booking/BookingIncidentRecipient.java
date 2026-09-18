package cl.helvoca.booking;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking_incident_recipient",
        uniqueConstraints = @UniqueConstraint(name = "uq_booking_incident_recipient_customer",
                columnNames = {"campaign_id", "customer_id"}))
public class BookingIncidentRecipient {
    public enum ChannelPreference { CHEAPEST, WHATSAPP, CALL }
    public enum Status { PREPARED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "booking_ids_json", nullable = false, columnDefinition = "jsonb")
    private List<String> bookingIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_preference", nullable = false, length = 20)
    private ChannelPreference channelPreference;

    @Column(name = "content_text", nullable = false, columnDefinition = "text")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PREPARED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.PREPARED;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public List<String> getBookingIds() { return bookingIds; }
    public void setBookingIds(List<String> bookingIds) { this.bookingIds = bookingIds; }
    public ChannelPreference getChannelPreference() { return channelPreference; }
    public void setChannelPreference(ChannelPreference channelPreference) { this.channelPreference = channelPreference; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
