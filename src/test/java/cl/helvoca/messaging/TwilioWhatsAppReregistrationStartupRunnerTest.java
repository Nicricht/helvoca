package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioWhatsAppReregistrationStartupRunnerTest {

    @Test
    void reregistersConfiguredLockedSenderWithoutDeletingIt() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");
        props.setPublicBaseUrl("https://helvoca.example");

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {
                  "senders": [{
                    "sid": "XEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "status": "OFFLINE",
                    "sender_id": "whatsapp:+14355652512",
                    "configuration": {"waba_id": "123456789", "verification_method": "sms"},
                    "webhook": {
                      "callback_url": "https://helvoca.example/webhooks/v1/twilio/whatsapp",
                      "callback_method": "POST"
                    },
                    "profile": {"name": "Helvoca"},
                    "offline_reasons": [{"code": "63051", "message": "locked"}]
                  }]
                }
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(201);
        when(createResponse.body()).thenReturn("""
                {
                  "sid": "XEbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "status": "ONLINE",
                  "sender_id": "whatsapp:+14355652512"
                }
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, createResponse);

        var runner = new TwilioWhatsAppReregistrationStartupRunner(
                true, "+14355652512", props, http);

        var result = runner.reregisterOnce();

        assertTrue(result.accepted());
        assertEquals(201, result.httpStatus());
        assertEquals("ONLINE", result.status());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = captor.getAllValues().get(1);
        assertEquals("POST", request.method());
        assertEquals("https://messaging.twilio.com/v2/Channels/Senders", request.uri().toString());
    }

    @Test
    void forcesReregistrationEvenWhenSenderLooksOnline() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");
        props.setPublicBaseUrl("https://helvoca.example");

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {
                  "senders": [{
                    "sid": "XEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "status": "ONLINE",
                    "sender_id": "whatsapp:+14355652512",
                    "configuration": {"waba_id": "123456789", "verification_method": "sms"},
                    "profile": {"name": "Helvoca"},
                    "webhook": {
                      "callback_url": "https://helvoca.example/webhooks/v1/twilio/whatsapp",
                      "callback_method": "POST"
                    }
                  }]
                }
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(409);
        when(createResponse.body()).thenReturn("""
                {"code":63110,"message":"Sender already registered"}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, createResponse);

        var runner = new TwilioWhatsAppReregistrationStartupRunner(
                true, "+14355652512", props, http);

        var result = runner.reregisterOnce();

        assertFalse(result.accepted());
        assertEquals(409, result.httpStatus());
        assertEquals("63110", result.errorCode());
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void reportsTwilioReregistrationFailureWithoutDeletingOrSendingMessages() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("""
                {
                  "senders": [{
                    "sid": "XEaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "status": "OFFLINE",
                    "sender_id": "whatsapp:+14355652512",
                    "configuration": {"waba_id": "123456789"},
                    "profile": {"name": "Helvoca"},
                    "offline_reasons": [{"code": "63051"}]
                  }]
                }
                """);

        @SuppressWarnings("unchecked")
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(409);
        when(createResponse.body()).thenReturn("""
                {"code":63110,"message":"Sender already registered"}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, createResponse);

        var runner = new TwilioWhatsAppReregistrationStartupRunner(
                true, "+14355652512", props, http);

        var result = runner.reregisterOnce();

        assertFalse(result.accepted());
        assertEquals(409, result.httpStatus());
        assertEquals("63110", result.errorCode());
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void disabledRunnerDoesNothing() throws Exception {
        TwilioProperties props = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);

        var runner = new TwilioWhatsAppReregistrationStartupRunner(
                false, "+14355652512", props, http);

        runner.run(null);

        verifyNoInteractions(http);
    }
}
