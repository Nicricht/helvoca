package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/webhooks/v1/twilio")
public class TwilioTemplateApprovalEventController {
    private static final Logger log = LoggerFactory.getLogger(TwilioTemplateApprovalEventController.class);
    static final String PATH = "/webhooks/v1/twilio/template-approval-events";
    private static final String EVENT_TYPE = "com.twilio.messaging.template.approval.updated";
    private static final Pattern BODY_SHA = Pattern.compile("(?:^|&)bodySHA256=([0-9a-fA-F]{64})(?:&|$)");

    private final TwilioProperties twilio;

    public TwilioTemplateApprovalEventController(TwilioProperties twilio) {
        this.twilio = twilio;
    }

    @PostMapping(value = "/template-approval-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            HttpServletRequest request,
            @RequestBody String rawBody) {
        String rawQuery = request.getQueryString();
        if (!validSignature(signature, rawQuery, rawBody)) {
            log.warn("Rejected Twilio template approval event with invalid signature");
            return ResponseEntity.status(403).build();
        }

        try {
            processEvents(rawBody);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Twilio template approval event parse failed type={}", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().build();
        }
    }

    boolean validSignature(String signature, String rawQuery, String rawBody) {
        if (signature == null || signature.isBlank()
                || !twilio.hasAuthToken()
                || !twilio.hasSecurePublicBaseUrl()
                || rawBody == null) {
            return false;
        }

        String bodySha = bodyShaFromQuery(rawQuery);
        if (bodySha == null) return false;

        try {
            String actualBodySha = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawBody.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(
                    actualBodySha.getBytes(StandardCharsets.US_ASCII),
                    bodySha.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }

            String url = twilio.absoluteWebhook(PATH)
                    + (rawQuery == null || rawQuery.isBlank() ? "" : "?" + rawQuery);
            RequestValidator validator = new RequestValidator(twilio.getAuthToken());
            return validator.validate(url, Map.of(), signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String bodyShaFromQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        Matcher matcher = BODY_SHA.matcher(rawQuery);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void processEvents(String rawBody) {
        JSONArray events = new JSONArray(rawBody);
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !EVENT_TYPE.equals(event.optString("type", ""))) continue;

            JSONObject data = event.optJSONObject("data");
            if (data == null || !"whatsapp".equalsIgnoreCase(data.optString("channel", ""))) continue;

            JSONObject template = data.optJSONObject("template");
            if (template == null) continue;

            log.info(
                    "WHATSAPP_TEMPLATE_APPROVAL_EVENT sid={} friendlyName={} updateType={} previous={} current={} language={}",
                    template.optString("sid", ""),
                    template.optString("friendlyName", ""),
                    data.optString("type", ""),
                    template.optString("previousValue", ""),
                    template.optString("currentValue", ""),
                    template.optString("language", "")
            );
        }
    }
}
