package cl.helvoca.ai.live;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class OpenAiWebhookVerifier {
    private final OpenAiLiveProperties properties;

    public OpenAiWebhookVerifier(OpenAiLiveProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String webhookId,
                          String timestamp,
                          String signatureHeader,
                          String rawBody) {
        if (!properties.hasWebhookSecret()
                || blank(webhookId) || blank(timestamp) || blank(signatureHeader) || rawBody == null) {
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }

        long tolerance = Math.max(30, properties.getWebhookToleranceSeconds());
        long now = Instant.now().getEpochSecond();
        if (timestampSeconds < now - tolerance || timestampSeconds > now + tolerance) {
            return false;
        }

        try {
            byte[] secret = decodeSecret(properties.getWebhookSecret().trim());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String signedPayload = webhookId + "." + timestamp + "." + rawBody;
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));

            for (String candidate : signatureHeader.trim().split("\\s+")) {
                String value = candidate.startsWith("v1,") ? candidate.substring(3) : candidate;
                if (MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        value.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] decodeSecret(String secret) {
        if (secret.startsWith("whsec_")) {
            return Base64.getDecoder().decode(secret.substring("whsec_".length()));
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
