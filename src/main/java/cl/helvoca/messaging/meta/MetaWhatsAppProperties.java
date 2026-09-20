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
    private boolean embeddedSignupEnabled = false;
    private String embeddedSignupAppId = "";
    private String embeddedSignupConfigId = "";
    private String embeddedSignupBusinessId = "";
    private String embeddedSignupSystemUserAccessToken = "";

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

    public String getEmbeddedSignupSystemUserAccessToken() {
        return clean(embeddedSignupSystemUserAccessToken);
    }

    public void setEmbeddedSignupSystemUserAccessToken(String embeddedSignupSystemUserAccessToken) {
        this.embeddedSignupSystemUserAccessToken = embeddedSignupSystemUserAccessToken;
    }

    public boolean hasEmbeddedSignupSystemUserAccessToken() {
        return !getEmbeddedSignupSystemUserAccessToken().isBlank();
    }

    public String graphApiRoot() {
        return getGraphBaseUrl() + "/" + getGraphApiVersion();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
