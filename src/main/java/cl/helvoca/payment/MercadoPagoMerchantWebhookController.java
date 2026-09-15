package cl.helvoca.payment;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks/v1/payments/mercadopago")
public class MercadoPagoMerchantWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoMerchantWebhookController.class);
    private static final Duration SIGNATURE_TOLERANCE = Duration.ofMinutes(5);

    private final PaymentProviderConfigService configs;
    private final PaymentProviderCredentialResolver credentials;
    private final PaymentWebhookService webhooks;

    public MercadoPagoMerchantWebhookController(PaymentProviderConfigService configs,
                                                 PaymentProviderCredentialResolver credentials,
                                                 PaymentWebhookService webhooks) {
        this.configs = configs;
        this.credentials = credentials;
        this.webhooks = webhooks;
    }

    @PostMapping("/{webhookKey}")
    public ResponseEntity<Void> webhook(
            @PathVariable UUID webhookKey,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "type", required = false) String queryType,
            @RequestParam(value = "data.id", required = false) String queryDataId,
            @RequestBody(required = false) String body) {
        PaymentProviderConfig config = configs.byWebhookKey(webhookKey);
        if (config == null) return ResponseEntity.notFound().build();
        PaymentProviderCredentialResolver.Credentials merchantCredentials = credentials
                .resolve(config.getCredentialRef())
                .orElse(null);
        if (merchantCredentials == null || !merchantCredentials.sandboxConfirmed()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        WebhookData data = parse(queryType, queryDataId, requestId, body);
        if (!"order".equalsIgnoreCase(data.topic())) return ResponseEntity.noContent().build();
        if (data.externalId() == null || data.externalId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (data.bodyExternalId() != null && !data.externalId().equals(data.bodyExternalId())) {
            return ResponseEntity.badRequest().build();
        }
        if (data.liveMode()) {
            log.error("Blocked LIVE Mercado Pago merchant webhook business={} order={}",
                    config.getBusinessId(), data.externalId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            WebhookSignatureValidator.validate(
                    signature,
                    requestId,
                    data.externalId(),
                    merchantCredentials.webhookSecret(),
                    SIGNATURE_TOLERANCE);
        } catch (MPInvalidWebhookSignatureException | IllegalArgumentException e) {
            log.warn("Rejected Mercado Pago merchant webhook business={} request={}",
                    config.getBusinessId(), requestId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaymentWebhookService.Result result = webhooks.processVerified(
                config.getBusinessId(),
                "mercadopago",
                data.eventId(),
                data.externalId(),
                data.externalReference(),
                body);
        return switch (result) {
            case PROCESSED, DUPLICATE, IGNORED -> ResponseEntity.noContent().build();
            case FAILED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        };
    }

    private static WebhookData parse(String queryType,
                                     String queryDataId,
                                     String requestId,
                                     String body) {
        String topic = queryType;
        String bodyExternalId = null;
        String externalReference = null;
        String eventId = requestId;
        boolean liveMode = false;

        if (body != null && !body.isBlank()) {
            try {
                JSONObject json = new JSONObject(body);
                if (topic == null || topic.isBlank()) topic = json.optString("type", null);
                Object rawEventId = json.opt("id");
                if (rawEventId != null && rawEventId != JSONObject.NULL) {
                    eventId = String.valueOf(rawEventId);
                }
                liveMode = json.optBoolean("live_mode", false);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    bodyExternalId = blankToNull(data.optString("id", null));
                    externalReference = blankToNull(data.optString("external_reference", null));
                }
            } catch (Exception ignored) { }
        }

        String externalId = blankToNull(queryDataId);
        if (externalId == null) externalId = bodyExternalId;
        if (eventId == null || eventId.isBlank()) {
            eventId = (externalId == null ? "unknown" : externalId) + ":" + (topic == null ? "order" : topic);
        }
        return new WebhookData(
                topic == null ? "" : topic.trim(),
                externalId,
                bodyExternalId,
                externalReference,
                eventId.trim(),
                liveMode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record WebhookData(String topic,
                               String externalId,
                               String bodyExternalId,
                               String externalReference,
                               String eventId,
                               boolean liveMode) {}
}
