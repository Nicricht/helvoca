package cl.helvoca.telephony.twilio;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class TwilioMediaRouteSigner {
    private static final String DOMAIN = "recepvoz-media-route-v1";
    private static final long MAX_AGE_SECONDS = 300L;
    private static final long FUTURE_SKEW_SECONDS = 30L;

    private final TwilioProperties properties;

    public TwilioMediaRouteSigner(TwilioProperties properties) {
        this.properties = properties;
    }

    public String sign(String businessPhone,
                       String callerPhone,
                       String callSid,
                       String providerId,
                       long issuedAtEpochSeconds) {
        validateRequired(businessPhone, callerPhone, callSid, providerId);
        if (!properties.hasAuthToken()) {
            throw new IllegalStateException("TWILIO_AUTH_TOKEN is required for media route signing");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(routeKey(), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload(businessPhone, callerPhone, callSid, providerId, issuedAtEpochSeconds)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign Twilio media route", e);
        }
    }

    public boolean verify(String businessPhone,
                          String callerPhone,
                          String callSid,
                          String providerId,
                          String issuedAtValue,
                          String token) {
        if (token == null || token.isBlank() || issuedAtValue == null || issuedAtValue.isBlank()) return false;
        long issuedAt;
        try {
            issuedAt = Long.parseLong(issuedAtValue.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (issuedAt < now - MAX_AGE_SECONDS || issuedAt > now + FUTURE_SKEW_SECONDS) return false;

        try {
            byte[] expected = sign(businessPhone, callerPhone, callSid, providerId, issuedAt)
                    .getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, token.trim().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] routeKey() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getAuthToken().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(DOMAIN.getBytes(StandardCharsets.UTF_8));
    }

    private static String payload(String businessPhone,
                                  String callerPhone,
                                  String callSid,
                                  String providerId,
                                  long issuedAtEpochSeconds) {
        return DOMAIN + "\n"
                + businessPhone.trim() + "\n"
                + callerPhone.trim() + "\n"
                + callSid.trim() + "\n"
                + providerId.trim().toLowerCase() + "\n"
                + issuedAtEpochSeconds;
    }

    private static void validateRequired(String businessPhone,
                                         String callerPhone,
                                         String callSid,
                                         String providerId) {
        if (blank(businessPhone) || blank(callerPhone) || blank(callSid) || blank(providerId)) {
            throw new IllegalArgumentException("Business, caller, CallSid and provider are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
