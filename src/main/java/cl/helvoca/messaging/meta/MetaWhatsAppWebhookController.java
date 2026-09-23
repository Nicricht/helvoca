package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.outbound.MetaWhatsAppDeliveryStatusService;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MetaWhatsAppTenantResolver tenantResolver;
    private final TenantDatabaseContext databaseContext;
    private final WhatsAppReceptionistService receptionist;

    @Autowired(required = false)
    private MetaWhatsAppDeliveryStatusService deliveryStatus;

    @Autowired(required = false)
    private MetaWhatsAppAudioTranscriptionService audioTranscription;

    public MetaWhatsAppWebhookController(MetaWhatsAppProperties properties,
                                         MetaWhatsAppTenantResolver tenantResolver,
                                         TenantDatabaseContext databaseContext,
                                         WhatsAppReceptionistService receptionist) {
        this.properties = properties;
        this.tenantResolver = tenantResolver;
        this.databaseContext = databaseContext;
        this.receptionist = receptionist;
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

        try {
            var messages = MetaWhatsAppPayloadParser.parseTextMessages(payload);
            var audioMessages = MetaWhatsAppPayloadParser.parseAudioMessages(payload);
            var statuses = MetaWhatsAppPayloadParser.parseDeliveryStatuses(payload);
            int processed = 0;
            int audioProcessed = 0;
            int statusProcessed = 0;
            int unresolved = 0;
            int failed = 0;
            int deferred = 0;

            for (MetaWhatsAppInboundMessage message : messages) {
                var route = tenantResolver.resolveRoute(message.phoneNumberId()).orElse(null);
                if (route == null) {
                    unresolved++;
                    continue;
                }

                try {
                    databaseContext.callAsTenant(route.businessId(), () ->
                            receptionist.handleResolved(
                                    message.messageId(),
                                    route.businessId(),
                                    route.phoneNumberId(),
                                    message.from(),
                                    message.text()));
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Meta WhatsApp message processing failed message={} business={} type={}",
                            message.messageId(),
                            route.businessId(),
                            e.getClass().getSimpleName());
                }
            }

            for (MetaWhatsAppInboundAudio message : audioMessages) {
                var route = tenantResolver.resolveRoute(message.phoneNumberId()).orElse(null);
                if (route == null) {
                    unresolved++;
                    continue;
                }
                if (audioTranscription == null) {
                    failed++;
                    log.warn("Meta WhatsApp audio processing unavailable message={} business={}",
                            message.messageId(), route.businessId());
                    continue;
                }

                try {
                    databaseContext.callAsTenant(route.businessId(), () -> {
                        String transcript = audioTranscription.transcribe(
                                route.businessId(), message.mediaId());
                        receptionist.handleResolved(
                                message.messageId(),
                                route.businessId(),
                                route.phoneNumberId(),
                                message.from(),
                                transcript);
                        return null;
                    });
                    audioProcessed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Meta WhatsApp audio processing failed message={} business={} type={}",
                            message.messageId(),
                            route.businessId(),
                            e.getClass().getSimpleName());
                }
            }

            if (deliveryStatus != null) {
                for (MetaWhatsAppDeliveryStatus status : statuses) {
                    var route = tenantResolver.resolveRoute(status.phoneNumberId()).orElse(null);
                    if (route == null) {
                        unresolved++;
                        continue;
                    }

                    try {
                        MetaWhatsAppDeliveryStatusService.Result result = databaseContext.callAsTenant(
                                route.businessId(),
                                () -> deliveryStatus.apply(
                                        route.businessId(),
                                        status.messageId(),
                                        status.status(),
                                        status.occurredAt(),
                                        status.errorCode()));
                        if (result == MetaWhatsAppDeliveryStatusService.Result.NOT_FOUND) {
                            deferred++;
                        } else {
                            statusProcessed++;
                        }
                    } catch (Exception e) {
                        failed++;
                        log.warn("Meta WhatsApp status processing failed business={} type={}",
                                route.businessId(),
                                e.getClass().getSimpleName());
                    }
                }
            }

            log.info(
                    "Meta WhatsApp webhook textMessages={} audioMessages={} statuses={} processed={} audioProcessed={} statusProcessed={} unresolvedTenants={} deferredStatuses={} failed={} outboundDelivery=guarded",
                    messages.size(),
                    audioMessages.size(),
                    statuses.size(),
                    processed,
                    audioProcessed,
                    statusProcessed,
                    unresolved,
                    deferred,
                    failed);

            if (deferred > 0) return ResponseEntity.status(503).build();
            return failed == 0
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.status(500).build();
        } catch (Exception e) {
            log.warn("Meta WhatsApp webhook payload could not be parsed type={}", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().build();
        }
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
