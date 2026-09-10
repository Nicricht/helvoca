package cl.helvoca.ai.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.openai")
public class OpenAiRealtimeProperties {
    private String apiKey = "";
    private String realtimeModel = "gpt-realtime-2.1";
    private String voice = "marin";
    private String transcriptionModel = "gpt-live-transcribe";
    private String summaryModel = "gpt-5.6-luna";
    private String trialModel = "gpt-5.6-luna";
    private String realtimeUrl = "wss://api.openai.com/v1/realtime";
    private String responsesUrl = "https://api.openai.com/v1/responses";

    /**
     * Returns the API key bound through Spring configuration. Railway and other
     * platforms may also expose OPENAI_API_KEY directly to the process, so use
     * that raw environment variable as a safe fallback when property binding
     * leaves the configured value blank.
     */
    public String getApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String environmentApiKey = System.getenv("OPENAI_API_KEY");
        return environmentApiKey == null ? "" : environmentApiKey.trim();
    }

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getRealtimeModel() { return realtimeModel; }
    public void setRealtimeModel(String realtimeModel) { this.realtimeModel = realtimeModel; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getTranscriptionModel() { return transcriptionModel; }
    public void setTranscriptionModel(String transcriptionModel) { this.transcriptionModel = transcriptionModel; }
    public String getSummaryModel() { return summaryModel; }
    public void setSummaryModel(String summaryModel) { this.summaryModel = summaryModel; }
    public String getTrialModel() { return trialModel; }
    public void setTrialModel(String trialModel) { this.trialModel = trialModel; }
    public String getRealtimeUrl() { return realtimeUrl; }
    public void setRealtimeUrl(String realtimeUrl) { this.realtimeUrl = realtimeUrl; }
    public String getResponsesUrl() { return responsesUrl; }
    public void setResponsesUrl(String responsesUrl) { this.responsesUrl = responsesUrl; }

    public boolean hasApiKey() { return !getApiKey().isBlank(); }
}
