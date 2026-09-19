package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import com.twilio.security.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhooks/v1/twilio")
public class TwilioWhatsAppController {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppController.class);
    private static final String EMPTY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";
    private static final String PATH = "/webhooks/v1/twilio/whatsapp";
    private static final String STATUS_PATH = "/webhooks/v1/twilio/whatsapp/status";

    private final WhatsAppReceptionistService receptionist;
    private final WhatsAppProperties properties;
    private final TwilioProperties twilio;

    public TwilioWhatsAppController(WhatsAppReceptionistService receptionist,
                                    WhatsAppProperties properties,
                                    TwilioProperties twilio) {
        this.receptionist = receptionist;
        this.properties = properties;
        this.twilio = twilio;
    }

    @PostMapping(value = "/whatsapp", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> inbound(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam MultiValueMap<String, String> form) {
        if (!properties.isEnabled()) return ResponseEntity.ok(EMPTY_TWIML);
        if (!validSignature(PATH, signature, form)) {
            log.warn("Rejected WhatsApp webhook with invalid Twilio signature");
            return ResponseEntity.status(403).body(EMPTY_TWIML);
        }

        String sid = form.getFirst("MessageSid");
        try {
            String reply = receptionist.handle(sid, form.getFirst("From"), form.getFirst("To"), form.getFirst("Body"));
            log.info("WhatsApp inbound processed message={} replyChars={}", sid, reply == null ? 0 : reply.length());
            return ResponseEntity.ok(twimlMessage(reply));
        } catch (Exception e) {
            log.warn("WhatsApp webhook failed message={} type={}", sid, e.getClass().getSimpleName());
            return ResponseEntity.ok(EMPTY_TWIML);
        }
    }

    @PostMapping(value = "/whatsapp/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> status(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam MultiValueMap<String, String> form) {
        if (!validSignature(STATUS_PATH, signature, form)) {
            log.warn("Rejected WhatsApp status callback with invalid Twilio signature");
            return ResponseEntity.status(403).body(EMPTY_TWIML);
        }

        log.info("WhatsApp delivery status message={} status={} errorCode={} channelStatus={}",
                form.getFirst("MessageSid"),
                form.getFirst("MessageStatus"),
                form.getFirst("ErrorCode"),
                form.getFirst("ChannelStatusMessage"));
        return ResponseEntity.ok(EMPTY_TWIML);
    }

    private boolean validSignature(String path, String signature, MultiValueMap<String, String> form) {
        if (!properties.isWebhookValidationEnabled()) return true;
        if (signature == null || signature.isBlank() || !twilio.hasAuthToken() || !twilio.hasSecurePublicBaseUrl()) {
            return false;
        }
        Map<String, String> params = new LinkedHashMap<>();
        form.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) params.put(key, values.get(0));
        });
        RequestValidator validator = new RequestValidator(twilio.getAuthToken());
        return validator.validate(twilio.absoluteWebhook(path), params, signature);
    }

    static String twimlMessage(String reply) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message statusCallback=\""
                + STATUS_PATH + "\" action=\"" + STATUS_PATH + "\">"
                + escapeXml(reply) + "</Message></Response>";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
