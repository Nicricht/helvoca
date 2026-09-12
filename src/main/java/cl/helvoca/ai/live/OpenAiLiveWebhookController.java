package cl.helvoca.ai.live;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/webhooks/v1/openai/live", "/webhooks/v1/openai/live-v2"})
public class OpenAiLiveWebhookController {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveWebhookController.class);

    private final OpenAiWebhookVerifier verifier;
    private final OpenAiLiveSipService liveSip;

    public OpenAiLiveWebhookController(OpenAiWebhookVerifier verifier,
                                       OpenAiLiveSipService liveSip) {
        this.verifier = verifier;
        this.liveSip = liveSip;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(@RequestHeader(value = "webhook-id", required = false) String webhookId,
                                        @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "webhook-signature", required = false) String signature,
                                        @RequestBody String rawBody) {
        if (!verifier.verify(webhookId, timestamp, signature, rawBody)) {
            return ResponseEntity.badRequest().build();
        }

        final JSONObject event;
        try {
            event = new JSONObject(rawBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        String type = event.optString("type", "");
        if (!"live.transport.incoming".equals(type) && !"live.call.incoming".equals(type)) {
            return ResponseEntity.noContent().build();
        }
        if (!liveSip.isReady()) {
            log.warn("Received GPT-Live SIP webhook while Live SIP is not fully configured");
            return ResponseEntity.status(503).build();
        }

        try {
            liveSip.handleIncoming(webhookId, event);
            return ResponseEntity.ok().build();
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Rejected GPT-Live SIP webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Could not handle GPT-Live SIP webhook", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
