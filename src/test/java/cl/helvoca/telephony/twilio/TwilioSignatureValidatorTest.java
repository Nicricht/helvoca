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
import static org.mockito.Mockito.*;

class TwilioSignatureValidatorTest {
    private static final String TOKEN = "test-auth-token";
    private static final String SUB_TOKEN = "subaccount-auth-token";
    private static final String SUBACCOUNT_SID = "ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void validatesExactPublicWebhookUrlAndFormParameters() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setPublicBaseUrl("https://helvoca.example");
        TwilioAuthTokenResolver tokens = mock(TwilioAuthTokenResolver.class);
        when(tokens.resolve(null)).thenReturn(TOKEN);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties, tokens);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/v1/twilio/voice");
        request.addParameter("CallSid", "CA123");
        request.addParameter("From", "+56911111111");
        request.addParameter("To", "+56220000000");
        String url = "https://helvoca.example/webhooks/v1/twilio/voice";
        String signature = signature(url, Map.of(
                "CallSid", "CA123",
                "From", "+56911111111",
                "To", "+56220000000"), TOKEN);
        request.addHeader("X-Twilio-Signature", signature);

        assertTrue(validator.validateHttp(request));
        request.removeHeader("X-Twilio-Signature");
        request.addHeader("X-Twilio-Signature", "invalid");
        assertFalse(validator.validateHttp(request));
    }

    @Test
    void validatesWebhookUsingForwardedPublicUrlBehindProxy() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        TwilioAuthTokenResolver tokens = mock(TwilioAuthTokenResolver.class);
        when(tokens.resolve(null)).thenReturn(TOKEN);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties, tokens);

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
                "To", "+56911111111"), TOKEN));

        assertTrue(validator.validateHttp(request));
    }

    @Test
    void usesOwningSubaccountTokenWhenAccountSidIsPresent() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        properties.setPublicBaseUrl("https://recepvoz.cl");
        TwilioAuthTokenResolver tokens = mock(TwilioAuthTokenResolver.class);
        when(tokens.resolve(SUBACCOUNT_SID)).thenReturn(SUB_TOKEN);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties, tokens);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/v1/twilio/voice");
        request.addParameter("AccountSid", SUBACCOUNT_SID);
        request.addParameter("CallSid", "CA123");
        request.addParameter("From", "+56911111111");
        request.addParameter("To", "+14705331828");
        String url = "https://recepvoz.cl/webhooks/v1/twilio/voice";
        request.addHeader("X-Twilio-Signature", signature(url, Map.of(
                "AccountSid", SUBACCOUNT_SID,
                "CallSid", "CA123",
                "From", "+56911111111",
                "To", "+14705331828"), SUB_TOKEN));

        assertTrue(validator.validateHttp(request));
        verify(tokens).resolve(SUBACCOUNT_SID);
    }

    private static String signature(String url, Map<String, String> params, String token) throws Exception {
        StringBuilder value = new StringBuilder(url);
        new TreeMap<>(params).forEach((key, item) -> value.append(key).append(item));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
