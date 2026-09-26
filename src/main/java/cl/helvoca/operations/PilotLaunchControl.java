package cl.helvoca.operations;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_launch_control")
public class PilotLaunchControl {
    public enum Status { DRAFT, READY, RUNNING, PAUSED, COMPLETED }

    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "responsible_name", length = 180)
    private String responsibleName;

    @Column(name = "responsible_contact", length = 180)
    private String responsibleContact;

    @Column(columnDefinition = "text")
    private String goal;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) status = Status.DRAFT;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResponsibleName() { return responsibleName; }
    public void setResponsibleName(String responsibleName) { this.responsibleName = responsibleName; }
    public String getResponsibleContact() { return responsibleContact; }
    public void setResponsibleContact(String responsibleContact) { this.responsibleContact = responsibleContact; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public Instant getPlannedEndAt() { return plannedEndAt; }
    public void setPlannedEndAt(Instant plannedEndAt) { this.plannedEndAt = plannedEndAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
