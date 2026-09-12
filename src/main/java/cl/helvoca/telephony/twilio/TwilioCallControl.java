package cl.helvoca.telephony.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Carrier-control adapter for operations that require updating an in-progress
 * Twilio call. It deliberately contains no Twilio TTS/Gather fallback.
 */
@Component
public class TwilioCallControl {
    private static final Logger log = LoggerFactory.getLogger(TwilioCallControl.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern CALL_SID = Pattern.compile("^CA[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final String DEFAULT_API_BASE = "https://api.twilio.com";

    private final TwilioProperties properties;
    private final HttpClient http;
    private final String apiBase;

    @Autowired
    public TwilioCallControl(TwilioProperties properties) {
        this(properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                DEFAULT_API_BASE);
    }

    TwilioCallControl(TwilioProperties properties, HttpClient http, String apiBase) {
        this.properties = properties;
        this.http = http;
        this.apiBase = trimTrailingSlash(apiBase);
    }

    public boolean transferToHuman(String accountSid, String callSid, String targetPhone) {
        if (!validCall(accountSid, callSid) || targetPhone == null || !E164.matcher(targetPhone.trim()).matches()) {
            log.warn("Rejected invalid live Twilio transfer request for call {}", safeSid(callSid));
            return false;
        }

        String target = targetPhone.trim();
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Dial timeout=\"20\" answerOnBridge=\"true\"><Number>"
                + escapeXml(target)
                + "</Number></Dial><Hangup/></Response>";
        boolean accepted = updateCallTwiml(accountSid, callSid, twiml);
        if (accepted) log.info("Twilio accepted human transfer for call {}", callSid);
        return accepted;
    }

    public boolean failGracefully(String accountSid, String callSid) {
        if (!validCall(accountSid, callSid)) return false;
        return updateCallTwiml(accountSid, callSid,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>");
    }

    private boolean updateCallTwiml(String accountSid, String callSid, String twiml) {
        String authToken = properties.getAuthToken();
        if (authToken == null || authToken.isBlank()) {
            log.warn("Cannot update Twilio call {} because TWILIO_AUTH_TOKEN is missing", safeSid(callSid));
            return false;
        }
        try {
            String credentials = accountSid + ":" + authToken.trim();
            String authorization = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String body = "Twiml=" + URLEncoder.encode(twiml, StandardCharsets.UTF_8);
            URI uri = URI.create(apiBase + "/2010-04-01/Accounts/" + accountSid + "/Calls/" + callSid + ".json");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return true;
            log.warn("Twilio rejected live call update call={} http={}", callSid, response.statusCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Could not update live Twilio call {}: {}", safeSid(callSid), e.getMessage());
            return false;
        }
    }

    private boolean validCall(String accountSid, String callSid) {
        return accountSid != null && ACCOUNT_SID.matcher(accountSid).matches()
                && callSid != null && CALL_SID.matcher(callSid).matches();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return DEFAULT_API_BASE;
        String out = value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String safeSid(String value) {
        if (value == null) return "unknown";
        return value.length() <= 10 ? value : value.substring(0, 10) + "…";
    }
}
