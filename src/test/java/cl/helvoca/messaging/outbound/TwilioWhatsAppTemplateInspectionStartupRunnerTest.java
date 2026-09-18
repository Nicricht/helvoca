package cl.helvoca.messaging.outbound;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioWhatsAppTemplateInspectionStartupRunnerTest {
    @Test
    void queriesContentAndApprovalsWhenEnabled() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"contents":[
                  {"sid":"HXaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","friendly_name":"booking_confirmation","language":"es",
                   "types":{"twilio/text":{"body":"Reserva confirmada"}},
                   "approvals":{"whatsapp":{"status":"approved","name":"booking_confirmation","category":"UTILITY"}}}
                ]}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TwilioWhatsAppTemplateInspectionStartupRunner runner =
                new TwilioWhatsAppTemplateInspectionStartupRunner(true, props, http);

        assertDoesNotThrow(() -> runner.run(null));
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                "https://content.twilio.com/v1/ContentAndApprovals?PageSize=100",
                captor.getValue().uri().toString());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties props = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);
        new TwilioWhatsAppTemplateInspectionStartupRunner(false, props, http).run(null);
        verifyNoInteractions(http);
    }
}
