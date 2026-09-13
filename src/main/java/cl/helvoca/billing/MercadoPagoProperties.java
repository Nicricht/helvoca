package cl.helvoca.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mercadopago")
public class MercadoPagoProperties {
    private boolean enabled;
    private String accessToken = "";
    private String webhookSecret = "";
    private String backUrl = "";
    private long webhookToleranceSeconds = 300;

    public boolean isEnabled() { return envBoolean("MERCADOPAGO_ENABLED", enabled); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAccessToken() { return env("MERCADOPAGO_ACCESS_TOKEN", accessToken); }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getWebhookSecret() { return env("MERCADOPAGO_WEBHOOK_SECRET", webhookSecret); }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getBackUrl() { return env("MERCADOPAGO_BACK_URL", backUrl); }
    public void setBackUrl(String backUrl) { this.backUrl = backUrl; }

    public long getWebhookToleranceSeconds() {
        String value = System.getenv("MERCADOPAGO_WEBHOOK_TOLERANCE_SECONDS");
        if (value != null) {
            try { return Math.max(30, Long.parseLong(value)); }
            catch (NumberFormatException ignored) { }
        }
        return Math.max(30, webhookToleranceSeconds);
    }

    public void setWebhookToleranceSeconds(long webhookToleranceSeconds) {
        this.webhookToleranceSeconds = webhookToleranceSeconds;
    }

    public boolean checkoutConfigured() {
        return isEnabled() && !getAccessToken().isBlank() && getBackUrl().startsWith("https://");
    }

    public boolean webhookConfigured() {
        return isEnabled() && !getAccessToken().isBlank() && !getWebhookSecret().isBlank();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? (fallback == null ? "" : fallback.trim()) : value.trim();
    }

    private static boolean envBoolean(String name, boolean fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
