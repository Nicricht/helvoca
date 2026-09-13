package cl.helvoca.billing;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_subscription")
public class BusinessSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "grace_until")
    private Instant graceUntil;

    @Column(name = "external_customer_id", length = 160)
    private String externalCustomerId;

    @Column(name = "external_subscription_id", length = 160)
    private String externalSubscriptionId;

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
    public PlanCode getPlanCode() { return planCode; }
    public void setPlanCode(PlanCode planCode) { this.planCode = planCode; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public Instant getGraceUntil() { return graceUntil; }
    public void setGraceUntil(Instant graceUntil) { this.graceUntil = graceUntil; }
    public String getExternalCustomerId() { return externalCustomerId; }
    public void setExternalCustomerId(String externalCustomerId) { this.externalCustomerId = externalCustomerId; }
    public String getExternalSubscriptionId() { return externalSubscriptionId; }
    public void setExternalSubscriptionId(String externalSubscriptionId) { this.externalSubscriptionId = externalSubscriptionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
