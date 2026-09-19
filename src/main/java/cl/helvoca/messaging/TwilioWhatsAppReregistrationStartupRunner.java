package cl.helvoca.messaging;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class TwilioWhatsAppReregistrationStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppReregistrationStartupRunner.class);
    private static final String SENDERS_API = "https://messaging.twilio.com/v2/Channels/Senders";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern SENDER_SID = Pattern.compile("^XE[0-9a-fA-F]{32}$");
    private static final int POLL_ATTEMPTS = 12;
    private static final long POLL_MILLIS = 15_000L;

    private final boolean enabled;
    private final String senderE164;
    private final TwilioProperties twilio;
    private final HttpClient http;

    @Autowired
    public TwilioWhatsAppReregistrationStartupRunner(
            @Value("${HELVOCA_WHATSAPP_REREGISTER_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_SENDER_E164:}") String senderE164,
            TwilioProperties twilio) {
        this(enabled, senderE164, twilio,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppReregistrationStartupRunner(boolean enabled,
                                              String senderE164,
                                              TwilioProperties twilio,
                                              HttpClient http) {
        this.enabled = enabled;
        this.senderE164 = senderE164 == null ? "" : senderE164.trim();
        this.twilio = twilio;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        Thread worker = new Thread(this::runSafely, "whatsapp-reregister-63051");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSafely() {
        try {
            ReregistrationAttempt attempt = reregisterOnce();
            if (!attempt.accepted()) return;

            String status = attempt.status();
            if ("ONLINE".equalsIgnoreCase(status)) {
                log.info("WHATSAPP_REREGISTER completed senderEnding={} status=ONLINE",
                        lastFour(senderE164));
                return;
            }

            String sid = attempt.senderSid();
            for (int i = 1; i <= POLL_ATTEMPTS; i++) {
                Thread.sleep(POLL_MILLIS);
                SenderState state = fetchSenderState(sid);
                log.info("WHATSAPP_REREGISTER poll={} senderEnding={} status={} offlineCode={}",
                        i, lastFour(senderE164), state.status(), state.offlineCode());
                if ("ONLINE".equalsIgnoreCase(state.status())) {
                    log.info("WHATSAPP_REREGISTER completed senderEnding={} status=ONLINE",
                            lastFour(senderE164));
                    return;
                }
                if ("PENDING_VERIFICATION".equalsIgnoreCase(state.status())) {
                    log.warn("WHATSAPP_REREGISTER requires verification senderEnding={} status=PENDING_VERIFICATION",
                            lastFour(senderE164));
                    return;
                }
            }

            log.warn("WHATSAPP_REREGISTER timed out senderEnding={} lastStatus={}",
                    lastFour(senderE164), status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("WHATSAPP_REREGISTER interrupted senderEnding={}", lastFour(senderE164));
        } catch (Exception e) {
            log.error("WHATSAPP_REREGISTER failed senderEnding={} type={} reason={}",
                    lastFour(senderE164), e.getClass().getSimpleName(), sanitize(e.getMessage()));
        }
    }

    ReregistrationAttempt reregisterOnce() throws Exception {
        validateConfiguration();

        String authorization = basicAuthorization();
        HttpResponse<String> listResponse = send(HttpRequest.newBuilder(
                        URI.create(SENDERS_API + "?Channel=whatsapp&PageSize=100"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", authorization)
                .GET()
                .build());

        if (!is2xx(listResponse.statusCode())) {
            TwilioError error = parseError(listResponse.body());
            log.error("WHATSAPP_REREGISTER lookup failed http={} code={} reason={}",
                    listResponse.statusCode(), error.code(), sanitize(error.message()));
            return new ReregistrationAttempt(false, "", "", listResponse.statusCode(),
                    error.code(), error.message());
        }

        JSONObject sender = findSender(new JSONObject(listResponse.body()).optJSONArray("senders"));
        String existingSid = sender.optString("sid", "").trim();
        String currentStatus = sender.optString("status", "").trim();
        String offlineCode = firstOfflineCode(sender.optJSONArray("offline_reasons"));
        log.info("WHATSAPP_REREGISTER current senderEnding={} sid={} status={} offlineCode={}",
                lastFour(senderE164), redactSid(existingSid), currentStatus, offlineCode);

        if ("ONLINE".equalsIgnoreCase(currentStatus)) {
            log.warn("WHATSAPP_REREGISTER forcing provider registration despite ONLINE senderEnding={} because delivery may still be locked",
                    lastFour(senderE164));
        }

        String profileName = sender.optJSONObject("profile") == null
                ? ""
                : sender.optJSONObject("profile").optString("name", "").trim();
        if (profileName.isBlank()) {
            throw new IllegalStateException("Existing Twilio sender has no profile.name to preserve");
        }

        JSONObject payload = new JSONObject()
                .put("sender_id", "whatsapp:" + senderE164)
                .put("profile", new JSONObject().put("name", profileName));

        JSONObject existingConfiguration = sender.optJSONObject("configuration");
        JSONObject configuration = new JSONObject();
        if (existingConfiguration != null) {
            String wabaId = existingConfiguration.optString("waba_id", "").trim();
            if (!wabaId.isBlank()) configuration.put("waba_id", wabaId);

            String verificationMethod = existingConfiguration.optString("verification_method", "").trim();
            if ("sms".equalsIgnoreCase(verificationMethod) || "voice".equalsIgnoreCase(verificationMethod)) {
                configuration.put("verification_method", verificationMethod.toLowerCase());
            }
        }
        if (!configuration.isEmpty()) payload.put("configuration", configuration);

        JSONObject existingWebhook = sender.optJSONObject("webhook");
        JSONObject webhook = new JSONObject();
        if (existingWebhook != null) {
            copyIfPresent(existingWebhook, webhook, "callback_url");
            copyIfPresent(existingWebhook, webhook, "callback_method");
            copyIfPresent(existingWebhook, webhook, "fallback_url");
            copyIfPresent(existingWebhook, webhook, "fallback_method");
            copyIfPresent(existingWebhook, webhook, "status_callback_url");
            copyIfPresent(existingWebhook, webhook, "status_callback_method");
        }
        if (webhook.isEmpty() && twilio.hasSecurePublicBaseUrl()) {
            webhook.put("callback_url", twilio.absoluteWebhook("/webhooks/v1/twilio/whatsapp"));
            webhook.put("callback_method", "POST");
        }
        if (!webhook.isEmpty()) payload.put("webhook", webhook);

        HttpResponse<String> createResponse = send(HttpRequest.newBuilder(URI.create(SENDERS_API))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build());

        if (!is2xx(createResponse.statusCode())) {
            TwilioError error = parseError(createResponse.body());
            log.error("WHATSAPP_REREGISTER request failed http={} code={} reason={}",
                    createResponse.statusCode(), error.code(), sanitize(error.message()));
            return new ReregistrationAttempt(false, existingSid, currentStatus,
                    createResponse.statusCode(), error.code(), error.message());
        }

        JSONObject created = new JSONObject(createResponse.body());
        String sid = created.optString("sid", existingSid).trim();
        String status = created.optString("status", "").trim();
        if (!SENDER_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio re-registration returned an invalid Sender SID");
        }

        log.info("WHATSAPP_REREGISTER accepted http={} senderEnding={} sid={} status={}",
                createResponse.statusCode(), lastFour(senderE164), redactSid(sid), status);
        return new ReregistrationAttempt(true, sid, status, createResponse.statusCode(), "", "");
    }

    private SenderState fetchSenderState(String sid) throws Exception {
        if (!SENDER_SID.matcher(sid).matches()) {
            throw new IllegalArgumentException("Invalid Twilio Sender SID");
        }

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(SENDERS_API + "/" + sid))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());

        if (!is2xx(response.statusCode())) {
            TwilioError error = parseError(response.body());
            throw new IllegalStateException("Twilio sender status failed http="
                    + response.statusCode() + " code=" + error.code() + " reason=" + sanitize(error.message()));
        }

        JSONObject json = new JSONObject(response.body());
        return new SenderState(
                json.optString("status", "").trim(),
                firstOfflineCode(json.optJSONArray("offline_reasons"))
        );
    }

    private void validateConfiguration() {
        if (!E164.matcher(senderE164).matches()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SENDER_E164 must use E.164 format");
        }
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken()) {
            throw new IllegalStateException("Twilio credentials are required");
        }
    }

    private JSONObject findSender(JSONArray senders) {
        if (senders == null) throw new IllegalStateException("Twilio returned no WhatsApp senders");
        String expected = "whatsapp:" + senderE164;
        for (int i = 0; i < senders.length(); i++) {
            JSONObject sender = senders.optJSONObject(i);
            if (sender != null && expected.equalsIgnoreCase(sender.optString("sender_id", ""))) {
                return sender;
            }
        }
        throw new IllegalStateException("Configured WhatsApp sender was not found in Twilio");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private String basicAuthorization() {
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyIfPresent(JSONObject source, JSONObject target, String key) {
        String value = source.optString(key, "").trim();
        if (!value.isBlank()) target.put(key, value);
    }

    private static boolean is2xx(int status) {
        return status >= 200 && status < 300;
    }

    private static TwilioError parseError(String body) {
        try {
            JSONObject json = new JSONObject(body == null ? "{}" : body);
            return new TwilioError(
                    String.valueOf(json.opt("code") == null ? "" : json.opt("code")),
                    json.optString("message", "")
            );
        } catch (Exception ignored) {
            return new TwilioError("", "Unparseable Twilio error response");
        }
    }

    private static String firstOfflineCode(JSONArray reasons) {
        if (reasons == null || reasons.isEmpty()) return "";
        JSONObject first = reasons.optJSONObject(0);
        return first == null ? "" : first.optString("code", "");
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim()
                .replaceAll("\\+[1-9][0-9]{7,14}", "[redacted-phone]");
        return clean.length() <= 300 ? clean : clean.substring(0, 300);
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    private static String redactSid(String sid) {
        if (sid == null || sid.length() < 8) return "";
        return sid.substring(0, 4) + "…" + sid.substring(sid.length() - 4);
    }

    record ReregistrationAttempt(boolean accepted,
                                 String senderSid,
                                 String status,
                                 int httpStatus,
                                 String errorCode,
                                 String errorMessage) { }

    record SenderState(String status, String offlineCode) { }

    record TwilioError(String code, String message) { }
}
