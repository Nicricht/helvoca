package cl.helvoca.telephony.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
public class TwilioCallControl {
    private static final Logger log = LoggerFactory.getLogger(TwilioCallControl.class);
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern CALL_SID = Pattern.compile("^CA[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final String API_BASE = "https://api.twilio.com";

    private final TwilioProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public TwilioCallControl(TwilioProperties properties) {
        this.properties = properties;
    }

    public boolean transferToHuman(String accountSid, String callSid, String targetPhone) {
        if (!validCall(accountSid, callSid)
                || targetPhone == null
                || !E164.matcher(targetPhone.trim()).matches()) return false;

        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Dial timeout=\"20\" answerOnBridge=\"true\"><Number>"
                + xml(targetPhone.trim())
                + "</Number></Dial><Hangup/></Response>";
        return update(accountSid, callSid, "Twiml", twiml);
    }

    public boolean hangup(String accountSid, String callSid) {
        if (!validCall(accountSid, callSid)) return false;
        return update(accountSid, callSid, "Status", "completed");
    }

    private boolean update(String accountSid, String callSid, String field, String value) {
        if (!properties.hasAuthToken()) return false;
        try {
            String credentials = accountSid + ":" + properties.getAuthToken().trim();
            String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String body = URLEncoder.encode(field, StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
            URI uri = URI.create(API_BASE + "/2010-04-01/Accounts/" + accountSid + "/Calls/" + callSid + ".json");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!accepted) log.warn("Twilio rejected call control call={} http={}", callSid, response.statusCode());
            return accepted;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Twilio call control failed call={}: {}", callSid, e.getMessage());
            return false;
        }
    }

    private static boolean validCall(String accountSid, String callSid) {
        return accountSid != null && ACCOUNT_SID.matcher(accountSid).matches()
                && callSid != null && CALL_SID.matcher(callSid).matches();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
