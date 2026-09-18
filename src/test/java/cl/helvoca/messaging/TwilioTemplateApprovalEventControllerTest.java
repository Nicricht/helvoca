package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TwilioTemplateApprovalEventControllerTest {

    @Test
    void validatesEventStreamsSignatureAndBodyHash() throws Exception {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAuthToken("test-token");
        twilio.setPublicBaseUrl("https://helvoca.example");

        TwilioTemplateApprovalEventController controller =
                new TwilioTemplateApprovalEventController(twilio);

        String body = "[{\"type\":\"com.twilio.messaging.template.approval.updated\",\"data\":{}}]";
        String bodySha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        String query = "bodySHA256=" + bodySha;
        String url = "https://helvoca.example/webhooks/v1/twilio/template-approval-events?" + query;

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("test-token".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(url.getBytes(StandardCharsets.UTF_8)));

        assertTrue(controller.validSignature(signature, query, body));
        assertFalse(controller.validSignature(signature, query, body + " "));
    }

    @Test
    void rejectsMissingSignature() {
        TwilioProperties twilio = new TwilioProperties();
        twilio.setAuthToken("test-token");
        twilio.setPublicBaseUrl("https://helvoca.example");
        TwilioTemplateApprovalEventController controller =
                new TwilioTemplateApprovalEventController(twilio);

        assertFalse(controller.validSignature(null, "bodySHA256=abc", "[]"));
    }
}
