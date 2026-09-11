package cl.helvoca.request;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_request")
public class BusinessRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "customer_id") private UUID customerId;
    @Column(name = "call_id") private UUID callId;
    @Column(name = "request_type", nullable = false, length = 80) private String requestType;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "contact_name", length = 200) private String contactName;
    @Column(name = "contact_phone", length = 30) private String contactPhone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RequestPriority priority = RequestPriority.NORMAL;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RequestStatus status = RequestStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RequestSource source = RequestSource.MANUAL;
    @Column(name = "details_json", columnDefinition = "text") private String detailsJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID v) { customerId = v; }
    public UUID getCallId() { return callId; }
    public void setCallId(UUID v) { callId = v; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String v) { requestType = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getContactName() { return contactName; }
    public void setContactName(String v) { contactName = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String v) { contactPhone = v; }
    public RequestPriority getPriority() { return priority; }
    public void setPriority(RequestPriority v) { priority = v; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus v) { status = v; }
    public RequestSource getSource() { return source; }
    public void setSource(RequestSource v) { source = v; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String v) { detailsJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
