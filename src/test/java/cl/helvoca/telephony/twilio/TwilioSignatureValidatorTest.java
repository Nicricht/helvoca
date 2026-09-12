package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioSignatureValidatorTest {
    private static final String TOKEN = "test-auth-token";

    @Test
    void validatesExactPublicWebhookUrlAndFormParameters() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken(TOKEN);
        properties.setPublicBaseUrl("https://helvoca.example");
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/v1/twilio/voice");
        request.addParameter("CallSid", "CA123");
        request.addParameter("From", "+56911111111");
        request.addParameter("To", "+56220000000");
        String url = "https://helvoca.example/webhooks/v1/twilio/voice";
        String signature = signature(url, Map.of(
                "CallSid", "CA123",
                "From", "+56911111111",
                "To", "+56220000000"));
        request.addHeader("X-Twilio-Signature", signature);

        assertTrue(validator.validateHttp(request));
        request.removeHeader("X-Twilio-Signature");
        request.addHeader("X-Twilio-Signature", "invalid");
        assertFalse(validator.validateHttp(request));
    }

    @Test
    void validatesWebhookUsingForwardedPublicUrlBehindProxy() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken(TOKEN);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/v1/twilio/outbound-test");
        request.setScheme("http");
        request.setServerName("railway.internal");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "helvoca-api-production.up.railway.app");
        request.addParameter("CallSid", "CA123");
        request.addParameter("From", "+14355652512");
        request.addParameter("To", "+56911111111");

        String url = "https://helvoca-api-production.up.railway.app/webhooks/v1/twilio/outbound-test";
        request.addHeader("X-Twilio-Signature", signature(url, Map.of(
                "CallSid", "CA123",
                "From", "+14355652512",
                "To", "+56911111111")));

        assertTrue(validator.validateHttp(request));
    }

    private static String signature(String url, Map<String, String> params) throws Exception {
        StringBuilder value = new StringBuilder(url);
        new TreeMap<>(params).forEach((key, item) -> value.append(key).append(item));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
