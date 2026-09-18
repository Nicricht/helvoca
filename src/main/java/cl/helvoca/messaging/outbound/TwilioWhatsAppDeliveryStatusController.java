package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.WhatsAppProperties;
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
public class TwilioWhatsAppDeliveryStatusController {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppDeliveryStatusController.class);
    static final String PATH = "/webhooks/v1/twilio/whatsapp-status";

    private final TwilioWhatsAppDeliveryStatusService deliveryStatus;
    private final WhatsAppProperties whatsApp;
    private final TwilioProperties twilio;

    public TwilioWhatsAppDeliveryStatusController(
            TwilioWhatsAppDeliveryStatusService deliveryStatus,
            WhatsAppProperties whatsApp,
            TwilioProperties twilio) {
        this.deliveryStatus = deliveryStatus;
        this.whatsApp = whatsApp;
        this.twilio = twilio;
    }

    @PostMapping(value = "/whatsapp-status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam MultiValueMap<String, String> form) {
        if (!validSignature(signature, form)) {
            log.warn("Rejected WhatsApp status callback with invalid Twilio signature");
            return ResponseEntity.status(403).build();
        }

        String sid = form.getFirst("MessageSid");
        String state = form.getFirst("MessageStatus");
        String errorCode = form.getFirst("ErrorCode");
        TwilioWhatsAppDeliveryStatusService.Result result = deliveryStatus.apply(sid, state, errorCode);
        if (result == TwilioWhatsAppDeliveryStatusService.Result.NOT_FOUND) {
            log.info("Deferring WhatsApp status callback until message SID is durable");
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean validSignature(String signature, MultiValueMap<String, String> form) {
        if (!whatsApp.isWebhookValidationEnabled()) return true;
        if (signature == null || signature.isBlank() || !twilio.hasAuthToken() || !twilio.hasSecurePublicBaseUrl()) {
            return false;
        }
        Map<String, String> params = new LinkedHashMap<>();
        form.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) params.put(key, values.get(0));
        });
        return new RequestValidator(twilio.getAuthToken())
                .validate(twilio.absoluteWebhook(PATH), params, signature);
    }
}
