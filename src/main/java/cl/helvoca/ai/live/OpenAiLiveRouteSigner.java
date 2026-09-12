package cl.helvoca.ai.live;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class OpenAiLiveRouteSigner {
    private static final long ROUTE_MAX_AGE_SECONDS = 300L;
    private static final long ROUTE_MAX_FUTURE_SKEW_SECONDS = 30L;
    private static final String DOMAIN = "recepvoz-sip-route-v1";

    private final OpenAiLiveProperties properties;

    public OpenAiLiveRouteSigner(OpenAiLiveProperties properties) {
        this.properties = properties;
    }

    public String sign(String businessPhone,
                       String callerPhone,
                       String twilioCallSid,
                       long issuedAtEpochSeconds) {
        if (!properties.hasWebhookSecret()) {
            throw new IllegalStateException("OPENAI_WEBHOOK_SECRET is required for GPT-Live SIP routing");
        }
        if (blank(businessPhone) || blank(callerPhone) || blank(twilioCallSid)) {
            throw new IllegalArgumentException("Business phone, caller phone and Twilio CallSid are required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(routeKey(), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload(
                    businessPhone, callerPhone, twilioCallSid, issuedAtEpochSeconds)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign GPT-Live SIP route", e);
        }
    }

    public boolean verify(String businessPhone,
                          String callerPhone,
                          String twilioCallSid,
                          String issuedAtValue,
                          String token) {
        if (blank(token) || blank(issuedAtValue)) return false;

        final long issuedAt;
        try {
            issuedAt = Long.parseLong(issuedAtValue.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (issuedAt < now - ROUTE_MAX_AGE_SECONDS || issuedAt > now + ROUTE_MAX_FUTURE_SKEW_SECONDS) {
            return false;
        }

        try {
            return MessageDigest.isEqual(
                    sign(businessPhone, callerPhone, twilioCallSid, issuedAt)
                            .getBytes(StandardCharsets.UTF_8),
                    token.trim().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] routeKey() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                properties.getWebhookSecret().trim().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return mac.doFinal(DOMAIN.getBytes(StandardCharsets.UTF_8));
    }

    private static String payload(String businessPhone,
                                  String callerPhone,
                                  String twilioCallSid,
                                  long issuedAtEpochSeconds) {
        return DOMAIN + "\n"
                + businessPhone.trim() + "\n"
                + callerPhone.trim() + "\n"
                + twilioCallSid.trim() + "\n"
                + issuedAtEpochSeconds;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
