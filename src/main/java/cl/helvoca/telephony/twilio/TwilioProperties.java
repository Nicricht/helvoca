package cl.helvoca.telephony.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {
    private String authToken = "";
    private String publicBaseUrl = "";

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public boolean hasAuthToken() { return authToken != null && !authToken.isBlank(); }
}
