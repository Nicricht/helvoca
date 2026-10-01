package cl.helvoca.platform;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "demo_profile")
public class DemoProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(nullable = false, length = 60)
    private String timezone = "America/Santiago";

    @Column(nullable = false, length = 10)
    private String language = "es";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "catalog_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> catalog = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hours_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> hours = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> knowledge = new LinkedHashMap<>();

    @Column(nullable = false, columnDefinition = "text")
    private String greeting;

    @Column(columnDefinition = "text")
    private String instructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_json", nullable = false, columnDefinition = "jsonb")
    private List<String> capabilities = new ArrayList<>();

    @Column(name = "presenter_notes", columnDefinition = "text")
    private String presenterNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadata = new LinkedHashMap<>();

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
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Map<String, Object> getCatalog() { return catalog; }
    public void setCatalog(Map<String, Object> catalog) { this.catalog = catalog; }
    public Map<String, Object> getHours() { return hours; }
    public void setHours(Map<String, Object> hours) { this.hours = hours; }
    public Map<String, Object> getKnowledge() { return knowledge; }
    public void setKnowledge(Map<String, Object> knowledge) { this.knowledge = knowledge; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public String getPresenterNotes() { return presenterNotes; }
    public void setPresenterNotes(String presenterNotes) { this.presenterNotes = presenterNotes; }
    public Map<String, Object> getSourceMetadata() { return sourceMetadata; }
    public void setSourceMetadata(Map<String, Object> sourceMetadata) { this.sourceMetadata = sourceMetadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
