package cl.helvoca.messaging.outbound;

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

@Component
public class TwilioWhatsAppTemplateInspectionStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppTemplateInspectionStartupRunner.class);
    private static final String CONTENT_AND_APPROVALS =
            "https://content.twilio.com/v1/ContentAndApprovals?PageSize=100";

    private final boolean enabled;
    private final TwilioProperties twilio;
    private final HttpClient http;

    @Autowired
    public TwilioWhatsAppTemplateInspectionStartupRunner(
            @Value("${HELVOCA_WHATSAPP_TEMPLATE_INSPECT_ON_STARTUP:false}") boolean enabled,
            TwilioProperties twilio) {
        this(enabled, twilio, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    TwilioWhatsAppTemplateInspectionStartupRunner(boolean enabled,
                                                  TwilioProperties twilio,
                                                  HttpClient http) {
        this.enabled = enabled;
        this.twilio = twilio;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!twilio.hasAccountSid() || !twilio.hasAuthToken()) {
            throw new IllegalStateException("Twilio credentials are required for template inspection");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(CONTENT_AND_APPROVALS))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", basicAuthorization())
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twilio template inspection failed with HTTP " + response.statusCode());
            }

            JSONArray contents = new JSONObject(response.body()).optJSONArray("contents");
            int approved = 0;
            if (contents != null) {
                for (int i = 0; i < contents.length(); i++) {
                    JSONObject content = contents.optJSONObject(i);
                    if (content == null) continue;
                    JSONObject whatsapp = content.optJSONObject("approvals") == null
                            ? null
                            : content.optJSONObject("approvals").optJSONObject("whatsapp");
                    String status = whatsapp == null ? "" : whatsapp.optString("status", "");
                    if (!"approved".equalsIgnoreCase(status)) continue;
                    approved++;
                    log.info(
                            "WHATSAPP_TEMPLATE_APPROVED sid={} name={} language={} status={}",
                            content.optString("sid", ""),
                            content.optString("friendly_name", ""),
                            content.optString("language", ""),
                            status.toLowerCase()
                    );
                }
            }
            log.info("WHATSAPP_TEMPLATE_INSPECTION approvedCount={}", approved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio template inspection interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Twilio template inspection failed", e);
        }
    }

    private String basicAuthorization() {
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
