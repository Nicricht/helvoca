package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class TwilioAuthTokenResolver {
    private static final String ACCOUNTS_API = "https://api.twilio.com/2010-04-01/Accounts";
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");

    private final TwilioProperties properties;
    private final String configuredSubaccountSid;
    private final HttpClient http;
    private volatile String cachedSubaccountToken;

    @Autowired
    public TwilioAuthTokenResolver(
            TwilioProperties properties,
            @Value("${HELVOCA_TWILIO_SUBACCOUNT_SID:}") String configuredSubaccountSid) {
        this(
                properties,
                configuredSubaccountSid,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioAuthTokenResolver(
            TwilioProperties properties,
            String configuredSubaccountSid,
            HttpClient http) {
        this.properties = properties;
        this.configuredSubaccountSid = clean(configuredSubaccountSid);
        this.http = http;
    }

    public String resolve(String requestAccountSid) {
        String accountSid = clean(requestAccountSid);
        String parentSid = clean(properties.getAccountSid());
        if (accountSid.isBlank() || accountSid.equals(parentSid)) {
            return properties.hasAuthToken() ? clean(properties.getAuthToken()) : "";
        }
        if (!ACCOUNT_SID.matcher(accountSid).matches()
                || !accountSid.equals(configuredSubaccountSid)
                || !properties.hasAccountSid()
                || !properties.hasAuthToken()) {
            return "";
        }

        String cached = cachedSubaccountToken;
        if (cached != null && !cached.isBlank()) return cached;

        synchronized (this) {
            cached = cachedSubaccountToken;
            if (cached != null && !cached.isBlank()) return cached;

            try {
                String credentials = parentSid + ":" + clean(properties.getAuthToken());
                String basic = Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(ACCOUNTS_API + "/" + accountSid + ".json"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Basic " + basic)
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) return "";

                JSONObject json = new JSONObject(response.body());
                if (!accountSid.equals(clean(json.optString("sid", "")))) return "";
                if (!parentSid.equals(clean(json.optString("owner_account_sid", "")))) return "";

                String token = clean(json.optString("auth_token", ""));
                if (token.isBlank()) return "";
                cachedSubaccountToken = token;
                return token;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
