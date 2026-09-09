package cl.helvoca.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.voice")
public class VoiceProviderProperties {
    private String aiProvider = "openai";
    private String telephonyProvider = "twilio";

    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }

    public String getTelephonyProvider() { return telephonyProvider; }
    public void setTelephonyProvider(String telephonyProvider) { this.telephonyProvider = telephonyProvider; }
}
