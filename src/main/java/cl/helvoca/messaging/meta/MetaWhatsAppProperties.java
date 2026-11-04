package cl.helvoca.messaging.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.meta.whatsapp")
public class MetaWhatsAppProperties {
    private boolean enabled = false;
    private boolean webhookValidationEnabled = true;
    private String graphApiVersion = "v26.0";
    private String graphBaseUrl = "https://graph.facebook.com";
    private String verifyToken = "";
    private String appSecret = "";
    // Deliberately opt-in: standard /register can disconnect an existing Business App.
    private boolean standardRegistrationEnabled = false;
    private boolean coexistenceEnabled = false;
    private String coexistenceAllowedBusinessId = "";
    private String coexistenceAllowedPhone = "";
    private boolean embeddedSignupEnabled = false;
    private String embeddedSignupAppId = "";
    private String embeddedSignupConfigId = "";
    private String embeddedSignupBusinessId = "";
    private String embeddedSignupSystemUserId = "";
    private String embeddedSignupSystemUserAccessToken = "";
    private String embeddedSignupAdminSystemUserAccessToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWebhookValidationEnabled() {
        return webhookValidationEnabled;
    }

    public void setWebhookValidationEnabled(boolean webhookValidationEnabled) {
        this.webhookValidationEnabled = webhookValidationEnabled;
    }

    public String getGraphApiVersion() {
        return clean(graphApiVersion);
    }

    public void setGraphApiVersion(String graphApiVersion) {
        this.graphApiVersion = graphApiVersion;
    }

    public String getGraphBaseUrl() {
        String value = clean(graphBaseUrl);
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public void setGraphBaseUrl(String graphBaseUrl) {
        this.graphBaseUrl = graphBaseUrl;
    }

    public String getVerifyToken() {
        return clean(verifyToken);
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getAppSecret() {
        return clean(appSecret);
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean hasVerifyToken() {
        return !getVerifyToken().isBlank();
    }

    public boolean hasAppSecret() {
        return !getAppSecret().isBlank();
    }

    public boolean isStandardRegistrationEnabled() {
        return standardRegistrationEnabled;
    }

    public void setStandardRegistrationEnabled(boolean enabled) {
        this.standardRegistrationEnabled = enabled;
    }

    public boolean isCoexistenceEnabled() {
        return coexistenceEnabled;
    }

    public void setCoexistenceEnabled(boolean enabled) {
        this.coexistenceEnabled = enabled;
    }

    public String getCoexistenceAllowedBusinessId() {
        return clean(coexistenceAllowedBusinessId);
    }

    public void setCoexistenceAllowedBusinessId(String businessId) {
        this.coexistenceAllowedBusinessId = businessId;
    }

    public String getCoexistenceAllowedPhone() {
        return clean(coexistenceAllowedPhone);
    }

    public void setCoexistenceAllowedPhone(String phone) {
        this.coexistenceAllowedPhone = phone;
    }

    public boolean isEmbeddedSignupEnabled() {
        return embeddedSignupEnabled;
    }

    public void setEmbeddedSignupEnabled(boolean embeddedSignupEnabled) {
        this.embeddedSignupEnabled = embeddedSignupEnabled;
    }

    public String getEmbeddedSignupAppId() {
        return clean(embeddedSignupAppId);
    }

    public void setEmbeddedSignupAppId(String embeddedSignupAppId) {
        this.embeddedSignupAppId = embeddedSignupAppId;
    }

    public boolean hasEmbeddedSignupAppId() {
        return !getEmbeddedSignupAppId().isBlank();
    }

    public String getEmbeddedSignupConfigId() {
        return clean(embeddedSignupConfigId);
    }

    public void setEmbeddedSignupConfigId(String embeddedSignupConfigId) {
        this.embeddedSignupConfigId = embeddedSignupConfigId;
    }

    public boolean hasEmbeddedSignupConfigId() {
        return !getEmbeddedSignupConfigId().isBlank();
    }

    public String getEmbeddedSignupBusinessId() {
        return clean(embeddedSignupBusinessId);
    }

    public void setEmbeddedSignupBusinessId(String embeddedSignupBusinessId) {
        this.embeddedSignupBusinessId = embeddedSignupBusinessId;
    }

    public boolean hasEmbeddedSignupBusinessId() {
        return !getEmbeddedSignupBusinessId().isBlank();
    }

    public String getEmbeddedSignupSystemUserId() {
        return clean(embeddedSignupSystemUserId);
    }

    public void setEmbeddedSignupSystemUserId(String embeddedSignupSystemUserId) {
        this.embeddedSignupSystemUserId = embeddedSignupSystemUserId;
    }

    public boolean hasEmbeddedSignupSystemUserId() {
        return !getEmbeddedSignupSystemUserId().isBlank();
    }

    public String getEmbeddedSignupSystemUserAccessToken() {
        return clean(embeddedSignupSystemUserAccessToken);
    }

    public void setEmbeddedSignupSystemUserAccessToken(String embeddedSignupSystemUserAccessToken) {
        this.embeddedSignupSystemUserAccessToken = embeddedSignupSystemUserAccessToken;
    }

    public boolean hasEmbeddedSignupSystemUserAccessToken() {
        return !getEmbeddedSignupSystemUserAccessToken().isBlank();
    }

    public String getEmbeddedSignupAdminSystemUserAccessToken() {
        return clean(embeddedSignupAdminSystemUserAccessToken);
    }

    public void setEmbeddedSignupAdminSystemUserAccessToken(String embeddedSignupAdminSystemUserAccessToken) {
        this.embeddedSignupAdminSystemUserAccessToken = embeddedSignupAdminSystemUserAccessToken;
    }

    public boolean hasEmbeddedSignupAdminSystemUserAccessToken() {
        return !getEmbeddedSignupAdminSystemUserAccessToken().isBlank();
    }

    public String graphApiRoot() {
        return getGraphBaseUrl() + "/" + getGraphApiVersion();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
