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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class TwilioTemplateEventStreamStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioTemplateEventStreamStartupRunner.class);

    private static final String DESCRIPTION = "helvoca-whatsapp-template-approval-events";
    private static final String EVENT_TYPE = "com.twilio.messaging.template.approval.updated";
    private static final String SINKS_API = "https://events.twilio.com/v1/Sinks";
    private static final String SUBSCRIPTIONS_API = "https://events.twilio.com/v1/Subscriptions";
    private static final Pattern SINK_SID = Pattern.compile("^DG[0-9a-fA-F]{32}$");
    private static final Pattern SUBSCRIPTION_SID = Pattern.compile("^DF[0-9a-fA-F]{32}$");

    private final boolean enabled;
    private final TwilioProperties twilio;
    private final HttpClient http;

    @Autowired
    public TwilioTemplateEventStreamStartupRunner(
            @Value("${HELVOCA_TWILIO_TEMPLATE_EVENTS_CONFIGURE_ON_STARTUP:false}") boolean enabled,
            TwilioProperties twilio) {
        this(enabled, twilio,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioTemplateEventStreamStartupRunner(boolean enabled,
                                           TwilioProperties twilio,
                                           HttpClient http) {
        this.enabled = enabled;
        this.twilio = twilio;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            ConfiguredStream configured = configureOnce();
            log.info(
                    "TWILIO_TEMPLATE_EVENTS_CONFIGURED sink={} subscription={}",
                    configured.sinkSid(), configured.subscriptionSid()
            );
        } catch (Exception e) {
            log.error("TWILIO_TEMPLATE_EVENTS_CONFIGURE_FAILED reason={}", rootMessage(e));
        }
    }

    ConfiguredStream configureOnce() throws Exception {
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken() || !twilio.hasSecurePublicBaseUrl()) {
            throw new IllegalStateException("Twilio credentials and secure public base URL are required");
        }

        String destination = twilio.absoluteWebhook(TwilioTemplateApprovalEventController.PATH);
        String sinkSid = findSink(destination);
        if (sinkSid == null) {
            sinkSid = createSink(destination);
        }

        String subscriptionSid = findSubscription(sinkSid);
        if (subscriptionSid == null) {
            subscriptionSid = createSubscription(sinkSid);
        }

        return new ConfiguredStream(sinkSid, subscriptionSid);
    }

    private String findSink(String destination) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(SINKS_API + "?PageSize=100"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());
        require2xx(response, "Twilio Event Streams sink list");

        JSONArray sinks = new JSONObject(response.body()).optJSONArray("sinks");
        if (sinks == null) return null;

        for (int i = 0; i < sinks.length(); i++) {
            JSONObject sink = sinks.optJSONObject(i);
            if (sink == null || !DESCRIPTION.equals(sink.optString("description", ""))) continue;

            String sid = sink.optString("sid", "").trim();
            if (!SINK_SID.matcher(sid).matches()) {
                throw new IllegalStateException("Existing Twilio Event Streams sink has invalid SID");
            }
            if (!"webhook".equalsIgnoreCase(sink.optString("sink_type", ""))) {
                throw new IllegalStateException("Existing Twilio Event Streams sink has unexpected type");
            }

            JSONObject configuration = sink.optJSONObject("sink_configuration");
            String existingDestination = configuration == null ? "" : configuration.optString("destination", "");
            if (!destination.equals(existingDestination)) {
                throw new IllegalStateException("Existing Twilio Event Streams sink destination does not match Helvoca");
            }
            return sid;
        }
        return null;
    }

    private String createSink(String destination) throws Exception {
        JSONObject configuration = new JSONObject()
                .put("destination", destination)
                .put("method", "POST")
                .put("batch_events", false);

        String body = form("Description", DESCRIPTION)
                + "&" + form("SinkType", "webhook")
                + "&" + form("SinkConfiguration", configuration.toString());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(SINKS_API))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        require2xx(response, "Twilio Event Streams sink create");

        JSONObject json = new JSONObject(response.body());
        String sid = json.optString("sid", "").trim();
        if (!SINK_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio Event Streams sink create returned no valid SID");
        }
        log.info("TWILIO_TEMPLATE_EVENTS_SINK_CREATED sink={} status={}",
                sid, json.optString("status", ""));
        return sid;
    }

    private String findSubscription(String sinkSid) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(SUBSCRIPTIONS_API + "?PageSize=100"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", basicAuthorization())
                .GET()
                .build());
        require2xx(response, "Twilio Event Streams subscription list");

        JSONArray subscriptions = new JSONObject(response.body()).optJSONArray("subscriptions");
        if (subscriptions == null) return null;

        for (int i = 0; i < subscriptions.length(); i++) {
            JSONObject subscription = subscriptions.optJSONObject(i);
            if (subscription == null
                    || !DESCRIPTION.equals(subscription.optString("description", ""))) continue;

            String sid = subscription.optString("sid", "").trim();
            if (!SUBSCRIPTION_SID.matcher(sid).matches()) {
                throw new IllegalStateException("Existing Twilio Event Streams subscription has invalid SID");
            }
            String existingSink = subscription.optString("sink_sid", "").trim();
            if (!sinkSid.equals(existingSink)) {
                throw new IllegalStateException("Existing Twilio Event Streams subscription points to another sink");
            }
            return sid;
        }
        return null;
    }

    private String createSubscription(String sinkSid) throws Exception {
        JSONObject type = new JSONObject()
                .put("type", EVENT_TYPE)
                .put("schema_version", 1);

        String body = form("Description", DESCRIPTION)
                + "&" + form("SinkSid", sinkSid)
                + "&" + form("Types", type.toString());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(SUBSCRIPTIONS_API))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuthorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        require2xx(response, "Twilio Event Streams subscription create");

        String sid = new JSONObject(response.body()).optString("sid", "").trim();
        if (!SUBSCRIPTION_SID.matcher(sid).matches()) {
            throw new IllegalStateException("Twilio Event Streams subscription create returned no valid SID");
        }
        log.info("TWILIO_TEMPLATE_EVENTS_SUBSCRIPTION_CREATED subscription={} sink={}", sid, sinkSid);
        return sid;
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

    private static String form(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void require2xx(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(action + " failed with HTTP " + response.statusCode());
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record ConfiguredStream(String sinkSid, String subscriptionSid) { }
}
