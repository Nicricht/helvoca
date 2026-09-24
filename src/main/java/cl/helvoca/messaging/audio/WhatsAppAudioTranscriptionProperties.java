package cl.helvoca.messaging.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.whatsapp.audio-transcription")
public class WhatsAppAudioTranscriptionProperties {
    private static final List<String> DEFAULT_ORDER = List.of("deepgram", "gemini", "openai");
    private static final Set<String> ALLOWED_PROVIDERS = Set.of("deepgram", "gemini", "openai");

    private List<String> providerOrder = DEFAULT_ORDER;
    private int immediateRetryMinMillis = 250;
    private int immediateRetryMaxMillis = 500;
    private int circuitFailureThreshold = 3;
    private int circuitCooldownSeconds = 60;
    private boolean deepgramEnabled = false;
    private String deepgramApiKey = "";
    private String deepgramEndpoint = "https://api.deepgram.com/v1/listen";
    private String deepgramModel = "nova-3";
    private String deepgramLanguage = "multi";
    private int deepgramTimeoutSeconds = 15;

    public List<String> getProviderOrder() {
        return List.copyOf(providerOrder);
    }

    public void setProviderOrder(List<String> providerOrder) {
        if (providerOrder == null || providerOrder.isEmpty()) {
            this.providerOrder = DEFAULT_ORDER;
            return;
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : providerOrder) {
            if (value == null) continue;
            String id = value.trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_PROVIDERS.contains(id)) cleaned.add(id);
        }
        this.providerOrder = cleaned.isEmpty() ? DEFAULT_ORDER : new ArrayList<>(cleaned);
    }

    public int getImmediateRetryMinMillis() { return immediateRetryMinMillis; }
    public void setImmediateRetryMinMillis(int value) { this.immediateRetryMinMillis = clamp(value, 0, 5000); }
    public int getImmediateRetryMaxMillis() { return Math.max(immediateRetryMinMillis, immediateRetryMaxMillis); }
    public void setImmediateRetryMaxMillis(int value) { this.immediateRetryMaxMillis = clamp(value, 0, 10000); }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int value) { this.circuitFailureThreshold = clamp(value, 1, 20); }
    public int getCircuitCooldownSeconds() { return circuitCooldownSeconds; }
    public void setCircuitCooldownSeconds(int value) { this.circuitCooldownSeconds = clamp(value, 1, 3600); }
    public boolean isDeepgramEnabled() { return deepgramEnabled; }
    public void setDeepgramEnabled(boolean deepgramEnabled) { this.deepgramEnabled = deepgramEnabled; }
    public String getDeepgramApiKey() { return clean(deepgramApiKey); }
    public void setDeepgramApiKey(String deepgramApiKey) { this.deepgramApiKey = clean(deepgramApiKey); }
    public String getDeepgramEndpoint() { return clean(deepgramEndpoint); }
    public void setDeepgramEndpoint(String deepgramEndpoint) { this.deepgramEndpoint = clean(deepgramEndpoint); }
    public String getDeepgramModel() { return clean(deepgramModel); }
    public void setDeepgramModel(String deepgramModel) { this.deepgramModel = clean(deepgramModel); }
    public String getDeepgramLanguage() { return clean(deepgramLanguage); }
    public void setDeepgramLanguage(String deepgramLanguage) { this.deepgramLanguage = clean(deepgramLanguage); }
    public int getDeepgramTimeoutSeconds() { return deepgramTimeoutSeconds; }
    public void setDeepgramTimeoutSeconds(int value) { this.deepgramTimeoutSeconds = clamp(value, 1, 120); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
