package cl.helvoca.ai.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.gemini.live")
public class GeminiLiveProperties {
    private boolean enabled = false;
    private boolean certificationSimulation = false;
    private String certificationCaller = "";
    private String apiKey = "";
    private String model = "gemini-3.1-flash-live-preview";
    private String voice = "Kore";
    private String websocketUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCertificationSimulation() { return certificationSimulation; }
    public void setCertificationSimulation(boolean certificationSimulation) { this.certificationSimulation = certificationSimulation; }
    public String getCertificationCaller() { return certificationCaller; }
    public void setCertificationCaller(String certificationCaller) { this.certificationCaller = certificationCaller; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getWebsocketUrl() { return websocketUrl; }
    public void setWebsocketUrl(String websocketUrl) { this.websocketUrl = websocketUrl; }

    public boolean ready() {
        return enabled
                && notBlank(apiKey)
                && notBlank(model)
                && notBlank(voice)
                && websocketUrl != null
                && websocketUrl.trim().startsWith("wss://");
    }

    public boolean certificationSimulationAllowedFor(String callerNumber) {
        return certificationSimulation
                && notBlank(certificationCaller)
                && notBlank(callerNumber)
                && certificationCaller.trim().equals(callerNumber.trim());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
