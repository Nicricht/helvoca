package cl.helvoca.telephony.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {
    private String accountSid = "";
    private String authToken = "";
    private String publicBaseUrl = "";
    private String mediaStreamPath = "/ws/v1/twilio/media";
    private String whatsappSandboxFrom = "";
    private boolean certificationIngressEnabled = false;
    private boolean provisioningEnabled = false;

    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getMediaStreamPath() { return mediaStreamPath; }
    public void setMediaStreamPath(String mediaStreamPath) { this.mediaStreamPath = mediaStreamPath; }
    public String getWhatsappSandboxFrom() { return whatsappSandboxFrom; }
    public void setWhatsappSandboxFrom(String whatsappSandboxFrom) { this.whatsappSandboxFrom = whatsappSandboxFrom; }
    public boolean isCertificationIngressEnabled() { return certificationIngressEnabled; }
    public void setCertificationIngressEnabled(boolean certificationIngressEnabled) {
        this.certificationIngressEnabled = certificationIngressEnabled;
    }
    public boolean isProvisioningEnabled() { return provisioningEnabled; }
    public void setProvisioningEnabled(boolean provisioningEnabled) { this.provisioningEnabled = provisioningEnabled; }

    public boolean hasAccountSid() { return accountSid != null && !accountSid.isBlank(); }
    public boolean hasAuthToken() { return authToken != null && !authToken.isBlank(); }

    public boolean hasSecurePublicBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) return false;
        try {
            URI uri = URI.create(publicBaseUrl.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean hasCommercialProvisioningCredentials() {
        return hasAccountSid() && hasAuthToken() && hasSecurePublicBaseUrl();
    }

    public String mediaStreamWebSocketUrl() {
        if (!hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("TWILIO_PUBLIC_BASE_URL must be an https:// URL for Media Streams");
        }
        String base = trimTrailingSlash(publicBaseUrl.trim());
        String path = mediaStreamPath == null || mediaStreamPath.isBlank()
                ? "/ws/v1/twilio/media"
                : (mediaStreamPath.startsWith("/") ? mediaStreamPath : "/" + mediaStreamPath);
        try {
            URI pathUri = URI.create(path);
            if (path.startsWith("//")
                    || pathUri.isAbsolute()
                    || pathUri.getRawAuthority() != null
                    || pathUri.getQuery() != null
                    || pathUri.getFragment() != null) {
                throw new IllegalStateException("TWILIO_MEDIA_STREAM_PATH must be a URL path without authority, query or fragment");
            }
            URI streamUri = URI.create("wss://" + base.substring("https://".length()) + path);
            if (!"wss".equalsIgnoreCase(streamUri.getScheme())
                    || streamUri.getHost() == null
                    || streamUri.getHost().isBlank()) {
                throw new IllegalStateException("TWILIO_MEDIA_STREAM_PATH produced an invalid WebSocket URL");
            }
            return streamUri.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TWILIO_MEDIA_STREAM_PATH must be a valid URL path", e);
        }
    }

    public String absoluteWebhook(String path) {
        if (!hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("TWILIO_PUBLIC_BASE_URL must be an https:// URL");
        }
        String suffix = path.startsWith("/") ? path : "/" + path;
        return trimTrailingSlash(publicBaseUrl.trim()) + suffix;
    }

    private static String trimTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
