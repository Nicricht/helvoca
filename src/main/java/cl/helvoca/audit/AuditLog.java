package cl.helvoca.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(name = "actor_type", length = 20)
    private String actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "actor_email", length = 180)
    private String actorEmail;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public Instant getCreatedAt() { return createdAt; }
}
