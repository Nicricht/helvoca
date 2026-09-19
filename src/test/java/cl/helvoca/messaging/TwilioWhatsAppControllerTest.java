package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TwilioWhatsAppControllerTest {

    @Test
    void disabledWebhookDoesNotInvokeReceptionist() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(false);
        TwilioProperties twilio = new TwilioProperties();

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .inbound(null, form());

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(receptionist);
    }

    @Test
    void invalidSignatureIsRejectedBeforeBusinessLogic() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(true);
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://example.test");
        twilio.setAuthToken("");

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .inbound("invalid", form());

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(receptionist);
    }

    @Test
    void validTwilioSignatureAllowsWebhookToReachReceptionist() throws Exception {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(true);
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://example.test");
        twilio.setAuthToken("test-auth-token");
        when(receptionist.handle("SM1", "+56911111111", "+56922222222", "hola"))
                .thenReturn("ok");

        var form = form();
        String webhookUrl = "https://example.test/webhooks/v1/twilio/whatsapp";
        String signature = twilioSignature(webhookUrl, form, "test-auth-token");

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .inbound(signature, form);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("statusCallback=\"/webhooks/v1/twilio/whatsapp/status\""));
        assertTrue(response.getBody().contains("action=\"/webhooks/v1/twilio/whatsapp/status\""));
        assertTrue(response.getBody().contains(">ok</Message>"));
        verify(receptionist).handle("SM1", "+56911111111", "+56922222222", "hola");
    }

    @Test
    void validStatusCallbackIsAcceptedAndDoesNotInvokeReceptionist() throws Exception {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(true);
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://example.test");
        twilio.setAuthToken("test-auth-token");

        LinkedMultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("MessageSid", "SM-out-1");
        payload.add("MessageStatus", "delivered");
        payload.add("ErrorCode", "");
        String callbackUrl = "https://example.test/webhooks/v1/twilio/whatsapp/status";
        String signature = twilioSignature(callbackUrl, payload, "test-auth-token");

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .status(signature, payload);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>", response.getBody());
        verifyNoInteractions(receptionist);
    }

    @Test
    void realisticTwilioPayloadPreservesMessageFields() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);
        TwilioProperties twilio = new TwilioProperties();

        LinkedMultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("MessageSid", "SM-real-123");
        payload.add("From", "whatsapp:+56912345678");
        payload.add("To", "whatsapp:+56987654321");
        payload.add("Body", "  Quiero reservar a las 18:30 & pagar  ");
        when(receptionist.handle(
                "SM-real-123",
                "whatsapp:+56912345678",
                "whatsapp:+56987654321",
                "  Quiero reservar a las 18:30 & pagar  "))
                .thenReturn("ok");

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .inbound(null, payload);

        assertEquals(200, response.getStatusCode().value());
        verify(receptionist).handle(
                "SM-real-123",
                "whatsapp:+56912345678",
                "whatsapp:+56987654321",
                "  Quiero reservar a las 18:30 & pagar  ");
    }

    @Test
    void acceptedWebhookEscapesReplyInTwiml() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);
        TwilioProperties twilio = new TwilioProperties();
        when(receptionist.handle("SM1", "+56911111111", "+56922222222", "hola"))
                .thenReturn("A&B <ok>");

        var response = new TwilioWhatsAppController(receptionist, properties, twilio)
                .inbound(null, form());

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("A&amp;B &lt;ok&gt;"));
        verify(receptionist).handle("SM1", "+56911111111", "+56922222222", "hola");
    }

    private static LinkedMultiValueMap<String, String> form() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("MessageSid", "SM1");
        form.add("From", "+56911111111");
        form.add("To", "+56922222222");
        form.add("Body", "hola");
        return form;
    }

    private static String twilioSignature(String url,
                                          LinkedMultiValueMap<String, String> form,
                                          String authToken) throws Exception {
        StringBuilder payload = new StringBuilder(url);
        form.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(key -> form.getOrDefault(key, java.util.List.of()).stream()
                        .sorted()
                        .forEach(value -> payload.append(key).append(value)));

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
