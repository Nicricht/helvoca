package cl.helvoca.phone;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
public class TwilioSubaccountVoiceBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioSubaccountVoiceBootstrapRunner.class);
    private static final String API_BASE = "https://api.twilio.com/2010-04-01/Accounts";
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final Pattern PHONE_SID = Pattern.compile("^PN[0-9a-fA-F]{32}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final boolean enabled;
    private final String subaccountSid;
    private final String phoneE164;
    private final TwilioProperties twilio;
    private final WhatsAppProperties whatsApp;
    private final PhoneNumberRepository phones;
    private final HttpClient http;

    @Autowired
    public TwilioSubaccountVoiceBootstrapRunner(
            @Value("${HELVOCA_TWILIO_SUBACCOUNT_VOICE_CONFIGURE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_TWILIO_SUBACCOUNT_SID:}") String subaccountSid,
            @Value("${HELVOCA_TWILIO_SUBACCOUNT_PHONE_E164:}") String phoneE164,
            TwilioProperties twilio,
            WhatsAppProperties whatsApp,
            PhoneNumberRepository phones) {
        this(
                enabled,
                subaccountSid,
                phoneE164,
                twilio,
                whatsApp,
                phones,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioSubaccountVoiceBootstrapRunner(
            boolean enabled,
            String subaccountSid,
            String phoneE164,
            TwilioProperties twilio,
            WhatsAppProperties whatsApp,
            PhoneNumberRepository phones,
            HttpClient http) {
        this.enabled = enabled;
        this.subaccountSid = clean(subaccountSid);
        this.phoneE164 = clean(phoneE164);
        this.twilio = twilio;
        this.whatsApp = whatsApp;
        this.phones = phones;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        validateConfiguration();

        PhoneNumber anchor = phones.findByPhoneNumberAndActiveTrue(whatsApp.getSandboxTenantPhone())
                .orElseThrow(() -> new IllegalStateException(
                        "WhatsApp Sandbox tenant phone must resolve to an active Helvoca tenant before voice bootstrap"));

        String authorization = basicAuthorization();
        PhoneLookup lookup = findPhone(authorization);
        configureVoiceWebhook(authorization, lookup.phoneSid());
        attachLocalRoute(anchor, lookup.phoneSid());

        log.info(
                "TWILIO_SUBACCOUNT_VOICE_CONFIGURED phoneEnding={} subaccountEnding={} tenantReady=true",
                lastFour(phoneE164),
                lastFour(subaccountSid));
    }

    private PhoneLookup findPhone(String authorization) throws Exception {
        String uri = API_BASE + "/" + subaccountSid
                + "/IncomingPhoneNumbers.json?PhoneNumber="
                + URLEncoder.encode(phoneE164, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", authorization)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (!is2xx(response.statusCode())) {
            throw new IllegalStateException("Twilio subaccount phone lookup failed with HTTP " + response.statusCode());
        }

        JSONArray numbers = new JSONObject(response.body()).optJSONArray("incoming_phone_numbers");
        if (numbers == null || numbers.length() != 1) {
            throw new IllegalStateException("Twilio subaccount must contain exactly one matching phone number");
        }
        JSONObject number = numbers.getJSONObject(0);
        String sid = clean(number.optString("sid", ""));
        String accountSid = clean(number.optString("account_sid", ""));
        String returnedPhone = clean(number.optString("phone_number", ""));
        if (!PHONE_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio subaccount phone SID is invalid");
        }
        if (!subaccountSid.equals(accountSid)) {
            throw new IllegalStateException("Twilio returned phone ownership for a different account");
        }
        if (!phoneE164.equals(returnedPhone)) {
            throw new IllegalStateException("Twilio returned a different phone number than requested");
        }
        return new PhoneLookup(sid);
    }

    private void configureVoiceWebhook(String authorization, String phoneSid) throws Exception {
        String body = form("VoiceUrl", twilio.absoluteWebhook("/webhooks/v1/twilio/voice"))
                + "&" + form("VoiceMethod", "POST")
                + "&" + form("StatusCallback", twilio.absoluteWebhook("/webhooks/v1/twilio/status"))
                + "&" + form("StatusCallbackMethod", "POST");

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(API_BASE + "/" + subaccountSid + "/IncomingPhoneNumbers/" + phoneSid + ".json"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", authorization)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (!is2xx(response.statusCode())) {
            throw new IllegalStateException("Twilio subaccount voice webhook update failed with HTTP " + response.statusCode());
        }

        JSONObject updated = new JSONObject(response.body());
        if (!subaccountSid.equals(clean(updated.optString("account_sid", "")))) {
            throw new IllegalStateException("Twilio voice update returned a different account owner");
        }
    }

    private void attachLocalRoute(PhoneNumber anchor, String phoneSid) {
        PhoneNumber phone = phones.findByPhoneNumber(phoneE164).orElseGet(PhoneNumber::new);
        if (phone.getBusinessId() != null && !phone.getBusinessId().equals(anchor.getBusinessId())) {
            throw new IllegalStateException("Twilio subaccount phone is already attached to a different Helvoca tenant");
        }

        phone.setBusinessId(anchor.getBusinessId());
        phone.setProvider("TWILIO");
        phone.setExternalId(phoneSid);
        phone.setPhoneNumber(phoneE164);
        phone.setActive(true);
        if (phone.getId() == null) {
            phone.setWhatsappEnabled(false);
        }
        phones.save(phone);
    }

    private void validateConfiguration() {
        if (!ACCOUNT_SID.matcher(subaccountSid).matches()) {
            throw new IllegalStateException("HELVOCA_TWILIO_SUBACCOUNT_SID must be a Twilio Account SID");
        }
        if (!E164.matcher(phoneE164).matches()) {
            throw new IllegalStateException("HELVOCA_TWILIO_SUBACCOUNT_PHONE_E164 must use E.164 format");
        }
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken() || !twilio.hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("Parent Twilio credentials and public HTTPS base URL are required");
        }
        if (!ACCOUNT_SID.matcher(clean(twilio.getAccountSid())).matches()) {
            throw new IllegalStateException("Parent TWILIO_ACCOUNT_SID is invalid");
        }
        if (whatsApp.getSandboxTenantPhone().isBlank()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SANDBOX_TENANT_PHONE_E164 must identify the target tenant");
        }
    }

    private String basicAuthorization() {
        String credentials = clean(twilio.getAccountSid()) + ":" + clean(twilio.getAuthToken());
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean is2xx(int status) {
        return status >= 200 && status < 300;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    record PhoneLookup(String phoneSid) { }
}
