package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.WhatsAppProperties;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwilioWhatsAppDeliveryStatusControllerTest {

    @Test
    void invalidSignatureIsRejectedBeforeDeliveryMutation() {
        TwilioWhatsAppDeliveryStatusService service = mock(TwilioWhatsAppDeliveryStatusService.class);
        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setWebhookValidationEnabled(true);
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example");
        twilio.setAuthToken("secret");

        var response = new TwilioWhatsAppDeliveryStatusController(service, whatsApp, twilio)
                .status("bad", form());

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void validSignatureForwardsTwilioDeliveryFields() throws Exception {
        TwilioWhatsAppDeliveryStatusService service = mock(TwilioWhatsAppDeliveryStatusService.class);
        WhatsAppProperties whatsApp = new WhatsAppProperties();
        whatsApp.setWebhookValidationEnabled(true);
        TwilioProperties twilio = new TwilioProperties();
        twilio.setPublicBaseUrl("https://helvoca.example");
        twilio.setAuthToken("secret");
        when(service.apply("SM123", "delivered", null))
                .thenReturn(TwilioWhatsAppDeliveryStatusService.Result.UPDATED);

        var payload = form();
        String signature = signature(
                "https://helvoca.example/webhooks/v1/twilio/whatsapp-status",
                payload,
                "secret");

        var response = new TwilioWhatsAppDeliveryStatusController(service, whatsApp, twilio)
                .status(signature, payload);

        assertEquals(204, response.getStatusCode().value());
        verify(service).apply("SM123", "delivered", null);
    }

    private static LinkedMultiValueMap<String, String> form() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("MessageSid", "SM123");
        form.add("MessageStatus", "delivered");
        form.add("To", "whatsapp:+56911111111");
        return form;
    }

    private static String signature(String url,
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
