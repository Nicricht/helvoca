package cl.helvoca.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
    private boolean enabled;
    private boolean webhookValidationEnabled = true;
    private int sessionHours = 24;

    public boolean isEnabled() {
        String env = System.getenv("TWILIO_WHATSAPP_ENABLED");
        return env == null ? enabled : Boolean.parseBoolean(env);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isWebhookValidationEnabled() {
        String env = System.getenv("TWILIO_WHATSAPP_WEBHOOK_VALIDATION_ENABLED");
        return env == null ? webhookValidationEnabled : Boolean.parseBoolean(env);
    }

    public void setWebhookValidationEnabled(boolean webhookValidationEnabled) {
        this.webhookValidationEnabled = webhookValidationEnabled;
    }

    public int getSessionHours() {
        String env = System.getenv("TWILIO_WHATSAPP_SESSION_HOURS");
        if (env != null) {
            try { return Math.max(1, Integer.parseInt(env)); }
            catch (NumberFormatException ignored) { }
        }
        return Math.max(1, sessionHours);
    }

    public void setSessionHours(int sessionHours) { this.sessionHours = sessionHours; }
}
