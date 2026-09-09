package cl.helvoca.telephony.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {
    private String authToken = "";
    private String publicBaseUrl = "";
    private String mediaStreamUrl = "";

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getMediaStreamUrl() { return mediaStreamUrl; }
    public void setMediaStreamUrl(String mediaStreamUrl) { this.mediaStreamUrl = mediaStreamUrl; }

    public boolean hasAuthToken() { return authToken != null && !authToken.isBlank(); }
    public boolean hasMediaStreamUrl() { return mediaStreamUrl != null && !mediaStreamUrl.isBlank(); }
}
