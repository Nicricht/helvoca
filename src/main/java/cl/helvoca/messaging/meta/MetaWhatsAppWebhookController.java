package cl.helvoca.messaging.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhooks/v1/meta")
public class MetaWhatsAppWebhookController {
    public static final String PATH = "/webhooks/v1/meta/whatsapp";

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppWebhookController.class);
    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final MetaWhatsAppProperties properties;

    public MetaWhatsAppWebhookController(MetaWhatsAppProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if (!SUBSCRIBE_MODE.equals(mode)
                || challenge == null
                || challenge.isBlank()
                || !properties.hasVerifyToken()
                || !constantTimeEquals(properties.getVerifyToken(), verifyToken)) {
            log.warn("Rejected Meta WhatsApp webhook verification");
            return ResponseEntity.status(403).body("");
        }

        log.info("Meta WhatsApp webhook verification accepted");
        return ResponseEntity.ok(challenge);
    }

    @PostMapping(value = "/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> inbound(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body) {

        byte[] payload = body == null ? new byte[0] : body;
        if (!validMetaSignature(signature, payload)) {
            log.warn("Rejected Meta WhatsApp webhook with invalid signature");
            return ResponseEntity.status(403).build();
        }

        if (!properties.isEnabled()) {
            log.info("Meta WhatsApp webhook authenticated but integration is disabled; payload ignored");
            return ResponseEntity.ok().build();
        }

        log.info("Meta WhatsApp webhook authenticated; message processing is not enabled yet");
        return ResponseEntity.ok().build();
    }

    private boolean validMetaSignature(String signature, byte[] body) {
        if (!properties.isWebhookValidationEnabled()) {
            return true;
        }
        if (!properties.hasAppSecret()
                || signature == null
                || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String providedHex = signature.substring(SIGNATURE_PREFIX.length()).trim();
        if (providedHex.length() != 64 || !providedHex.matches("[0-9a-fA-F]{64}")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getAppSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] provided = HexFormat.of().parseHex(providedHex);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            log.warn("Meta WhatsApp signature validation failed type={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
