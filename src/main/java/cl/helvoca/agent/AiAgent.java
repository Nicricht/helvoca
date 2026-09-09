package cl.helvoca.agent;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ai_agent")
public class AiAgent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 100)
    private String voice;

    @Column(nullable = false, columnDefinition = "text")
    private String greeting;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_agent_capability", joinColumns = @JoinColumn(name = "ai_agent_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 80)
    private Set<AiCapability> capabilities = EnumSet.allOf(AiCapability.class);

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Set<AiCapability> getCapabilities() { return capabilities; }
    public void setCapabilities(Set<AiCapability> capabilities) {
        this.capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(AiCapability.class)
                : EnumSet.copyOf(capabilities);
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
