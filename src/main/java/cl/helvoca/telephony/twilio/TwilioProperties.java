package cl.helvoca.telephony.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {
    private String authToken = "";
    private String publicBaseUrl = "";
    private String mediaStreamPath = "/ws/v1/twilio/media";

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getMediaStreamPath() { return mediaStreamPath; }
    public void setMediaStreamPath(String mediaStreamPath) { this.mediaStreamPath = mediaStreamPath; }

    public boolean hasAuthToken() { return authToken != null && !authToken.isBlank(); }

    public boolean hasSecurePublicBaseUrl() {
        return publicBaseUrl != null && publicBaseUrl.trim().startsWith("https://");
    }

    public String mediaStreamWebSocketUrl() {
        if (!hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("TWILIO_PUBLIC_BASE_URL must be an https:// URL for Media Streams");
        }
        String base = trimTrailingSlash(publicBaseUrl.trim());
        String path = mediaStreamPath == null || mediaStreamPath.isBlank()
                ? "/ws/v1/twilio/media"
                : (mediaStreamPath.startsWith("/") ? mediaStreamPath : "/" + mediaStreamPath);
        return "wss://" + base.substring("https://".length()) + path;
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
