package cl.helvoca.operations;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_operation")
public class BusinessOperation {
    public enum Type { ORDER, QUOTE, LEAD, DELIVERY, REQUEST, BOOKING, PAYMENT }
    public enum Status { DRAFT, AWAITING_CONFIRMATION, CONFIRMED, CANCELLED, EXPIRED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false)
    private UUID businessId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessOrder.Source source = BusinessOrder.Source.API;
    @Column(nullable = false)
    private Integer revision = 1;
    @Column(name = "confirmation_token")
    private UUID confirmationToken;
    @Column(name = "contact_name", length = 180)
    private String contactName;
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", length = 20)
    private BusinessOrder.FulfillmentType fulfillmentType;
    @Column(name = "delivery_zone_id")
    private UUID deliveryZoneId;
    @Column(name = "delivery_address", columnDefinition = "text")
    private String deliveryAddress;
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "delivery_fee", precision = 12, scale = 2)
    private BigDecimal deliveryFee;
    @Column(precision = 12, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, length = 3)
    private String currency = "CLP";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (revision == null || revision < 1) revision = 1;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public void setSourceReferenceId(UUID sourceReferenceId) { this.sourceReferenceId = sourceReferenceId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BusinessOrder.Source getSource() { return source; }
    public void setSource(BusinessOrder.Source source) { this.source = source; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public UUID getConfirmationToken() { return confirmationToken; }
    public void setConfirmationToken(UUID confirmationToken) { this.confirmationToken = confirmationToken; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public BusinessOrder.FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(BusinessOrder.FulfillmentType fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public UUID getDeliveryZoneId() { return deliveryZoneId; }
    public void setDeliveryZoneId(UUID deliveryZoneId) { this.deliveryZoneId = deliveryZoneId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
