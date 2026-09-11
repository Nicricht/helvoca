package cl.helvoca.ai.live;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiWebhookVerifierTest {

    @Test
    void acceptsValidCurrentSignatureAndRejectsTampering() throws Exception {
        OpenAiLiveProperties properties = new OpenAiLiveProperties();
        properties.setWebhookSecret("test-webhook-secret");
        properties.setWebhookToleranceSeconds(300);
        OpenAiWebhookVerifier verifier = new OpenAiWebhookVerifier(properties);

        String webhookId = "wh_test";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"type\":\"live.transport.incoming\"}";
        String signature = "v1," + sign(properties.getWebhookSecret(), webhookId + "." + timestamp + "." + body);

        assertTrue(verifier.verify(webhookId, timestamp, signature, body));
        assertFalse(verifier.verify(webhookId, timestamp, signature, body + " "));
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        OpenAiLiveProperties properties = new OpenAiLiveProperties();
        properties.setWebhookSecret("test-webhook-secret");
        properties.setWebhookToleranceSeconds(60);
        OpenAiWebhookVerifier verifier = new OpenAiWebhookVerifier(properties);

        String webhookId = "wh_old";
        String timestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        String body = "{}";
        String signature = "v1," + sign(properties.getWebhookSecret(), webhookId + "." + timestamp + "." + body);

        assertFalse(verifier.verify(webhookId, timestamp, signature, body));
    }

    private static String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
