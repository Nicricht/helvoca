package cl.helvoca.telephony.twilio.trial;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@ConfigurationProperties(prefix = "app.twilio.trial")
public class TrialVoiceProperties {
    private boolean enabled = false;
    private String phoneNumber = "";
    private String businessName = "Helvoca Restaurante Demo";
    private String greeting = "Hola, soy Helvoca. Puedes preguntarme por los servicios o pedirme una reserva.";
    private String language = "es-CL";
    private int maxTurns = 8;
    private String webhookSecret = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public boolean hasPhoneNumber() {
        return phoneNumber != null && phoneNumber.matches("^\\+[1-9]\\d{7,14}$");
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean matchesWebhookSecret(String candidate) {
        if (!hasWebhookSecret() || candidate == null || candidate.isBlank()) return false;
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
