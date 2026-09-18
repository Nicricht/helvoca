package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioWhatsAppWebhookStartupRunnerTest {
    @Test
    void configuresInboundWebhookForOnlineSender() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");
        props.setPublicBaseUrl("https://helvoca.example");

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {"senders":[{"sid":"XEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","status":"ONLINE","sender_id":"whatsapp:+14355652512"}]}
                """);
        @SuppressWarnings("unchecked")
        HttpResponse<String> updateResponse = mock(HttpResponse.class);
        when(updateResponse.statusCode()).thenReturn(200);
        when(updateResponse.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, updateResponse);

        TwilioWhatsAppWebhookStartupRunner runner = new TwilioWhatsAppWebhookStartupRunner(
                true, "+14355652512", props, http);

        runner.run(null);

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest update = captor.getAllValues().get(1);
        assertEquals("POST", update.method());
        assertEquals(
                "https://messaging.twilio.com/v2/Channels/Senders/XEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                update.uri().toString());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties props = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);
        new TwilioWhatsAppWebhookStartupRunner(false, "+14355652512", props, http).run(null);
        verifyNoInteractions(http);
    }
}
