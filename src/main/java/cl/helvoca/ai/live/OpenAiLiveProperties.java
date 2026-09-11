package cl.helvoca.ai.live;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.openai.live")
public class OpenAiLiveProperties {
    private boolean enabled = false;
    private String projectId = "";
    private String webhookSecret = "";
    private String model = "gpt-live-1";
    private String backendModel = "gpt-5.6-luna";
    private String voice = "marin";
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String sidebandBaseUrl = "wss://api.openai.com/v1";
    private int webhookToleranceSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBackendModel() { return backendModel; }
    public void setBackendModel(String backendModel) { this.backendModel = backendModel; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getSidebandBaseUrl() { return sidebandBaseUrl; }
    public void setSidebandBaseUrl(String sidebandBaseUrl) { this.sidebandBaseUrl = sidebandBaseUrl; }
    public int getWebhookToleranceSeconds() { return webhookToleranceSeconds; }
    public void setWebhookToleranceSeconds(int webhookToleranceSeconds) { this.webhookToleranceSeconds = webhookToleranceSeconds; }

    public boolean hasProjectId() {
        return projectId != null && projectId.trim().startsWith("proj_");
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean ready(String apiKey) {
        return enabled && hasProjectId() && hasWebhookSecret()
                && apiKey != null && !apiKey.isBlank();
    }

    public String normalizedApiBaseUrl() {
        return trimTrailingSlash(apiBaseUrl);
    }

    public String normalizedSidebandBaseUrl() {
        return trimTrailingSlash(sidebandBaseUrl);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        String out = value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
