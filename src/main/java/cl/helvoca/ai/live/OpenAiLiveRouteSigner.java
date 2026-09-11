package cl.helvoca.ai.live;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class OpenAiLiveRouteSigner {
    private final OpenAiLiveProperties properties;

    public OpenAiLiveRouteSigner(OpenAiLiveProperties properties) {
        this.properties = properties;
    }

    public String sign(String businessPhone, String callerPhone) {
        if (!properties.hasWebhookSecret()) {
            throw new IllegalStateException("OPENAI_WEBHOOK_SECRET is required for GPT-Live SIP routing");
        }
        if (blank(businessPhone) || blank(callerPhone)) {
            throw new IllegalArgumentException("Business and caller phone numbers are required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getWebhookSecret().trim().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(payload(businessPhone, callerPhone).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign GPT-Live SIP route", e);
        }
    }

    public boolean verify(String businessPhone, String callerPhone, String token) {
        if (blank(token)) return false;
        try {
            return MessageDigest.isEqual(
                    sign(businessPhone, callerPhone).getBytes(StandardCharsets.UTF_8),
                    token.trim().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String payload(String businessPhone, String callerPhone) {
        return businessPhone.trim() + "\n" + callerPhone.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
