package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

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
}
