package cl.helvoca.messaging.outbound;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_message",
        uniqueConstraints = @UniqueConstraint(name = "uq_outbound_message_idempotency",
                columnNames = {"business_id", "idempotency_key"}))
public class OutboundMessage {
    public enum Channel { WHATSAPP }
    public enum Purpose {
        PAYMENT_LINK, BOOKING_CONFIRMATION, MEETING_LINK, ORDER_STATUS,
        QUOTE, REMINDER, DELIVERY_STATUS, INCIDENT_NOTICE
    }
    public enum Status { PREPARED, QUEUED, SENT, FAILED, CANCELLED, BLOCKED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "operation_id", nullable = false) private UUID operationId;
    @Column(name = "recipient_identity_id", nullable = false) private UUID recipientIdentityId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Purpose purpose;
    @Column(name = "recipient_address", nullable = false, length = 180) private String recipientAddress;
    @Column(length = 40) private String provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PREPARED;
    @Column(name = "idempotency_key", nullable = false, length = 220) private String idempotencyKey;
    @Column(name = "content_text", nullable = false, columnDefinition = "text") private String contentText;
    @Column(name = "provider_message_id", length = 180) private String providerMessageId;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.PREPARED;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public UUID getRecipientIdentityId() { return recipientIdentityId; }
    public void setRecipientIdentityId(UUID recipientIdentityId) { this.recipientIdentityId = recipientIdentityId; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }
    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
