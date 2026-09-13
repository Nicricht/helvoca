package cl.helvoca.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.voice")
public class VoiceProviderProperties {
    private String aiProvider = "openai";
    private String telephonyProvider = "twilio";
    private List<String> providerOrder = new ArrayList<>(List.of("gemini", "openai-live"));

    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }

    public String getTelephonyProvider() { return telephonyProvider; }
    public void setTelephonyProvider(String telephonyProvider) { this.telephonyProvider = telephonyProvider; }

    public List<String> getProviderOrder() { return providerOrder; }
    public void setProviderOrder(List<String> providerOrder) {
        this.providerOrder = providerOrder == null ? new ArrayList<>() : new ArrayList<>(providerOrder);
    }
}
