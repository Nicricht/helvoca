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
    private String model = "gemini-3.8-live";
    private String voice = "Despina";
    private String websocketUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

    // Tuned for fast phone dialogue while preserving short natural pauses.
    private String startOfSpeechSensitivity = "START_SENSITIVITY_HIGH";
    private int prefixPaddingMs = 60;
    private String endOfSpeechSensitivity = "END_SENSITIVITY_LOW";
    private int silenceDurationMs = 300;

    // Local Twilio-side barge-in cuts queued playback before the provider round-trip
    // confirms the interruption. Gemini VAD remains authoritative for model turns.
    private boolean localBargeInEnabled = true;
    private int localBargeInMeanAmplitudeThreshold = 900;
    private int localBargeInSpeechFrames = 2;
    private int localBargeInReleaseFrames = 5;
    private int localBargeInRecentAssistantAudioMs = 1800;

    // Observability budgets. Exceeding them emits structured warnings; it never
    // changes business outcomes or skips backend validation.
    private int toolLatencyBudgetMs = 1200;
    private int responseLatencyBudgetMs = 3000;

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

    public String getStartOfSpeechSensitivity() {
        return "START_SENSITIVITY_LOW".equalsIgnoreCase(startOfSpeechSensitivity)
                ? "START_SENSITIVITY_LOW"
                : "START_SENSITIVITY_HIGH";
    }
    public void setStartOfSpeechSensitivity(String startOfSpeechSensitivity) {
        this.startOfSpeechSensitivity = startOfSpeechSensitivity;
    }
    public int getPrefixPaddingMs() { return clamp(prefixPaddingMs, 0, 1000); }
    public void setPrefixPaddingMs(int prefixPaddingMs) { this.prefixPaddingMs = prefixPaddingMs; }
    public String getEndOfSpeechSensitivity() {
        return "END_SENSITIVITY_HIGH".equalsIgnoreCase(endOfSpeechSensitivity)
                ? "END_SENSITIVITY_HIGH"
                : "END_SENSITIVITY_LOW";
    }
    public void setEndOfSpeechSensitivity(String endOfSpeechSensitivity) {
        this.endOfSpeechSensitivity = endOfSpeechSensitivity;
    }
    public int getSilenceDurationMs() { return clamp(silenceDurationMs, 100, 2000); }
    public void setSilenceDurationMs(int silenceDurationMs) { this.silenceDurationMs = silenceDurationMs; }

    public boolean isLocalBargeInEnabled() { return localBargeInEnabled; }
    public void setLocalBargeInEnabled(boolean localBargeInEnabled) { this.localBargeInEnabled = localBargeInEnabled; }
    public int getLocalBargeInMeanAmplitudeThreshold() {
        return clamp(localBargeInMeanAmplitudeThreshold, 100, 20_000);
    }
    public void setLocalBargeInMeanAmplitudeThreshold(int value) {
        this.localBargeInMeanAmplitudeThreshold = value;
    }
    public int getLocalBargeInSpeechFrames() { return clamp(localBargeInSpeechFrames, 1, 20); }
    public void setLocalBargeInSpeechFrames(int value) { this.localBargeInSpeechFrames = value; }
    public int getLocalBargeInReleaseFrames() { return clamp(localBargeInReleaseFrames, 1, 100); }
    public void setLocalBargeInReleaseFrames(int value) { this.localBargeInReleaseFrames = value; }
    public int getLocalBargeInRecentAssistantAudioMs() {
        return clamp(localBargeInRecentAssistantAudioMs, 250, 10_000);
    }
    public void setLocalBargeInRecentAssistantAudioMs(int value) {
        this.localBargeInRecentAssistantAudioMs = value;
    }
    public int getToolLatencyBudgetMs() { return clamp(toolLatencyBudgetMs, 100, 30_000); }
    public void setToolLatencyBudgetMs(int value) { this.toolLatencyBudgetMs = value; }
    public int getResponseLatencyBudgetMs() { return clamp(responseLatencyBudgetMs, 250, 30_000); }
    public void setResponseLatencyBudgetMs(int value) { this.responseLatencyBudgetMs = value; }

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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
