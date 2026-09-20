package cl.helvoca.messaging;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messaging_message")
public class MessagingMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "external_message_id", unique = true, length = 100)
    private String externalMessageId;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "reply_text", columnDefinition = "text")
    private String replyText;

    @Column(length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Column(name = "provider_delivery_status", length = 20)
    private String providerDeliveryStatus;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivery_updated_at")
    private Instant deliveryUpdatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public String getExternalMessageId() { return externalMessageId; }
    public void setExternalMessageId(String externalMessageId) { this.externalMessageId = externalMessageId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getProviderDeliveryStatus() { return providerDeliveryStatus; }
    public void setProviderDeliveryStatus(String providerDeliveryStatus) { this.providerDeliveryStatus = providerDeliveryStatus; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getDeliveryUpdatedAt() { return deliveryUpdatedAt; }
    public void setDeliveryUpdatedAt(Instant deliveryUpdatedAt) { this.deliveryUpdatedAt = deliveryUpdatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
