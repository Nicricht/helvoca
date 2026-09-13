package cl.helvoca.billing;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/webhooks/v1/mercadopago")
public class MercadoPagoWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoProperties properties;
    private final BillingSubscriptionService billing;

    public MercadoPagoWebhookController(MercadoPagoProperties properties,
                                        BillingSubscriptionService billing) {
        this.properties = properties;
        this.billing = billing;
    }

    @PostMapping
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "type", required = false) String queryType,
            @RequestParam(value = "topic", required = false) String queryTopic,
            @RequestParam(value = "data.id", required = false) String queryDataId,
            @RequestBody(required = false) String body) {
        if (!properties.isEnabled()) return ResponseEntity.notFound().build();
        if (!properties.webhookConfigured()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        WebhookData data = parse(queryType, queryTopic, queryDataId, body);
        if (data.id() == null || data.id().isBlank()) return ResponseEntity.badRequest().build();

        try {
            WebhookSignatureValidator.validate(
                    signature,
                    requestId,
                    data.id(),
                    properties.getWebhookSecret(),
                    Duration.ofSeconds(properties.getWebhookToleranceSeconds()));
        } catch (MPInvalidWebhookSignatureException | IllegalArgumentException e) {
            log.warn("Rejected Mercado Pago webhook request={} topic={}", requestId, data.topic());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            switch (data.topic()) {
                case "subscription_preapproval" -> billing.reconcileSubscription(data.id());
                case "subscription_authorized_payment" -> billing.reconcileAuthorizedPayment(data.id());
                default -> { return ResponseEntity.noContent().build(); }
            }
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Mercado Pago reconciliation failed request={} topic={} type={}",
                    requestId, data.topic(), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static WebhookData parse(String queryType, String queryTopic, String queryDataId, String body) {
        String topic = firstNonBlank(queryType, queryTopic);
        String id = queryDataId;
        if (body != null && !body.isBlank()) {
            try {
                JSONObject json = new JSONObject(body);
                topic = firstNonBlank(topic, json.optString("type", null), json.optString("topic", null));
                JSONObject data = json.optJSONObject("data");
                id = firstNonBlank(id, data == null ? null : data.optString("id", null));
            } catch (Exception ignored) { }
        }
        return new WebhookData(topic == null ? "" : topic.trim(), id == null ? null : id.trim());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private record WebhookData(String topic, String id) {}
}
