package cl.helvoca.ai.deepgram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Deepgram voice pipeline configuration. It is deliberately unavailable until
 * both an API key and an explicit data-processing approval are present.
 */
@Component
@ConfigurationProperties(prefix = "app.voice.deepgram")
public class DeepgramVoiceProperties {
    private boolean enabled = false;
    private boolean dataProcessingApproved = false;
    private String apiKey = "";
    private String sttEndpoint = "wss://api.deepgram.com/v1/listen";
    private String ttsEndpoint = "https://api.deepgram.com/v1/speak";
    private String sttModel = "nova-3";
    private String sttLanguage = "multi";
    private String sttLanguageHint = "es";
    private String ttsModel = "aura-2-celeste-es";
    private int connectTimeoutSeconds = 8;
    private int requestTimeoutSeconds = 15;
    private int maxTtsCharacters = 500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDataProcessingApproved() { return dataProcessingApproved; }
    public void setDataProcessingApproved(boolean approved) { this.dataProcessingApproved = approved; }
    public String getApiKey() { return clean(apiKey); }
    public void setApiKey(String apiKey) { this.apiKey = clean(apiKey); }
    public String getSttEndpoint() { return clean(sttEndpoint); }
    public void setSttEndpoint(String value) { this.sttEndpoint = clean(value); }
    public String getTtsEndpoint() { return clean(ttsEndpoint); }
    public void setTtsEndpoint(String value) { this.ttsEndpoint = clean(value); }
    public String getSttModel() { return clean(sttModel); }
    public void setSttModel(String value) { this.sttModel = clean(value); }
    public String getSttLanguage() { return clean(sttLanguage); }
    public void setSttLanguage(String value) { this.sttLanguage = clean(value); }
    public String getSttLanguageHint() { return clean(sttLanguageHint); }
    public void setSttLanguageHint(String value) { this.sttLanguageHint = clean(value); }
    public String getTtsModel() { return clean(ttsModel); }
    public void setTtsModel(String value) { this.ttsModel = clean(value); }
    public int getConnectTimeoutSeconds() { return clamp(connectTimeoutSeconds, 1, 30); }
    public void setConnectTimeoutSeconds(int value) { this.connectTimeoutSeconds = value; }
    public int getRequestTimeoutSeconds() { return clamp(requestTimeoutSeconds, 2, 60); }
    public void setRequestTimeoutSeconds(int value) { this.requestTimeoutSeconds = value; }
    public int getMaxTtsCharacters() { return clamp(maxTtsCharacters, 80, 1200); }
    public void setMaxTtsCharacters(int value) { this.maxTtsCharacters = value; }

    public boolean ready() {
        return enabled && dataProcessingApproved && !getApiKey().isBlank()
                && "nova-3".equals(getSttModel())
                && "multi".equals(getSttLanguage())
                && "aura-2-celeste-es".equals(getTtsModel())
                && validEndpoint(getSttEndpoint(), "wss")
                && validEndpoint(getTtsEndpoint(), "https");
    }

    private static boolean validEndpoint(String value, String scheme) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            return scheme.equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
