package cl.helvoca.telephony.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {
    private String accountSid = "";
    private String authToken = "";
    private String publicBaseUrl = "";
    private String mediaStreamUrl = "";

    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getMediaStreamUrl() { return mediaStreamUrl; }
    public void setMediaStreamUrl(String mediaStreamUrl) { this.mediaStreamUrl = mediaStreamUrl; }

    public boolean hasAccountSid() { return accountSid != null && !accountSid.isBlank(); }
    public boolean hasAuthToken() { return authToken != null && !authToken.isBlank(); }
    public boolean hasPublicBaseUrl() { return publicBaseUrl != null && !publicBaseUrl.isBlank(); }
    public boolean hasMediaStreamUrl() { return mediaStreamUrl != null && !mediaStreamUrl.isBlank(); }
    public boolean hasCommercialProvisioningCredentials() {
        return hasAccountSid() && hasAuthToken() && hasPublicBaseUrl();
    }
}
