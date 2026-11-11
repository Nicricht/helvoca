package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Strict opt-in for a single authorized tenant and carrier destination.
 * A key alone must never make a paid provider eligible.
 */
@Component
@ConfigurationProperties(prefix = "app.voice.deepgram-economy")
public class DeepgramEconomyVoiceProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String pinnedBusinessId = "";
    private String pinnedDestinationNumber = "";
    // Public pay-as-you-go reference rates, not an invoice or guaranteed savings.
    private double novaUsdPerMinute = 0.0092;
    private double auraUsdPerThousandChars = 0.030;
    private double geminiReferenceUsdPerMinute = 0.023;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPinnedBusinessId() { return pinnedBusinessId; }
    public void setPinnedBusinessId(String value) { this.pinnedBusinessId = value; }
    public String getPinnedDestinationNumber() { return pinnedDestinationNumber; }
    public void setPinnedDestinationNumber(String value) { this.pinnedDestinationNumber = value; }
    public double getNovaUsdPerMinute() { return novaUsdPerMinute; }
    public void setNovaUsdPerMinute(double value) { novaUsdPerMinute = value; }
    public double getAuraUsdPerThousandChars() { return auraUsdPerThousandChars; }
    public void setAuraUsdPerThousandChars(double value) { auraUsdPerThousandChars = value; }
    public double getGeminiReferenceUsdPerMinute() { return geminiReferenceUsdPerMinute; }
    public void setGeminiReferenceUsdPerMinute(double value) { geminiReferenceUsdPerMinute = value; }

    public boolean configuredFor(String destination) {
        return enabled && apiKey != null && !apiKey.isBlank()
                && pinnedBusinessId != null && !pinnedBusinessId.isBlank()
                && pinnedDestinationNumber != null && !pinnedDestinationNumber.isBlank()
                && pinnedDestinationNumber.equals(destination)
                && validPinnedUuid() && economicalForCharacters(180);
    }

    public boolean permits(RealtimeCallContext context) {
        return context != null && context.businessId() != null
                && context.voiceOverride() == null
                && configuredFor(context.destinationNumber())
                && context.businessId().toString().equalsIgnoreCase(pinnedBusinessId.trim());
    }

    public boolean economicalForCharacters(int outputChars) {
        if (outputChars < 0 || outputChars > 220 || !Double.isFinite(novaUsdPerMinute)
                || !Double.isFinite(auraUsdPerThousandChars)
                || !Double.isFinite(geminiReferenceUsdPerMinute)
                || novaUsdPerMinute <= 0 || auraUsdPerThousandChars <= 0
                || geminiReferenceUsdPerMinute <= 0) return false;
        return novaUsdPerMinute + auraUsdPerThousandChars * outputChars / 1000.0
                < geminiReferenceUsdPerMinute;
    }

    private boolean validPinnedUuid() {
        try { UUID.fromString(pinnedBusinessId.trim()); return true; }
        catch (RuntimeException ignored) { return false; }
    }
}
