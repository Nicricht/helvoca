package cl.helvoca.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {
    private boolean enabled;
    private boolean webhookValidationEnabled = true;
    private int sessionHours = 24;
    private boolean sandboxEnabled;
    private String sandboxNumber = "+14155238886";
    private String sandboxTenantPhone = "";

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

    public boolean isSandboxEnabled() {
        String env = System.getenv("HELVOCA_WHATSAPP_SANDBOX_ENABLED");
        return env == null ? sandboxEnabled : Boolean.parseBoolean(env);
    }

    public void setSandboxEnabled(boolean sandboxEnabled) { this.sandboxEnabled = sandboxEnabled; }

    public String getSandboxNumber() {
        String env = System.getenv("HELVOCA_WHATSAPP_SANDBOX_E164");
        String value = env == null || env.isBlank() ? sandboxNumber : env;
        return value == null ? "" : value.trim();
    }

    public void setSandboxNumber(String sandboxNumber) { this.sandboxNumber = sandboxNumber; }

    public String getSandboxTenantPhone() {
        String explicit = System.getenv("HELVOCA_WHATSAPP_SANDBOX_TENANT_PHONE_E164");
        if (explicit != null && !explicit.isBlank()) return explicit.trim();
        String liveSender = System.getenv("HELVOCA_WHATSAPP_SENDER_E164");
        if (liveSender != null && !liveSender.isBlank()) return liveSender.trim();
        return sandboxTenantPhone == null ? "" : sandboxTenantPhone.trim();
    }

    public void setSandboxTenantPhone(String sandboxTenantPhone) {
        this.sandboxTenantPhone = sandboxTenantPhone;
    }

    public String resolveTenantDestination(String inboundTo) {
        if (!isSandboxEnabled()) return inboundTo;
        if (inboundTo == null || !inboundTo.equals(getSandboxNumber())) return inboundTo;
        String routed = getSandboxTenantPhone();
        if (routed.isBlank()) {
            throw new IllegalStateException("WhatsApp Sandbox tenant phone is not configured");
        }
        return routed;
    }
}
