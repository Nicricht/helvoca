package cl.helvoca.business;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_profile")
public class BusinessProfile {
    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "preset_key", length = 60)
    private String presetKey;

    @Column(name = "public_description", columnDefinition = "text")
    private String publicDescription;

    @Column(name = "public_phone", length = 32)
    private String publicPhone;

    @Column(name = "public_email", length = 180)
    private String publicEmail;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "address_line", length = 250)
    private String addressLine;

    @Column(length = 120)
    private String commune;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "CLP";

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

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public String getPresetKey() { return presetKey; }
    public void setPresetKey(String presetKey) { this.presetKey = presetKey; }
    public String getPublicDescription() { return publicDescription; }
    public void setPublicDescription(String publicDescription) { this.publicDescription = publicDescription; }
    public String getPublicPhone() { return publicPhone; }
    public void setPublicPhone(String publicPhone) { this.publicPhone = publicPhone; }
    public String getPublicEmail() { return publicEmail; }
    public void setPublicEmail(String publicEmail) { this.publicEmail = publicEmail; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCommune() { return commune; }
    public void setCommune(String commune) { this.commune = commune; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
