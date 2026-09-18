package cl.helvoca.messaging;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioTemplateEventStreamStartupRunnerTest {

    @Test
    void createsWebhookSinkAndSubscriptionWhenMissing() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");
        props.setPublicBaseUrl("https://helvoca.example");

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> sinks = mock(HttpResponse.class);
        when(sinks.statusCode()).thenReturn(200);
        when(sinks.body()).thenReturn("{\"sinks\":[]}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> sinkCreate = mock(HttpResponse.class);
        when(sinkCreate.statusCode()).thenReturn(201);
        when(sinkCreate.body()).thenReturn(
                "{\"sid\":\"DGaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"status\":\"active\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> subscriptions = mock(HttpResponse.class);
        when(subscriptions.statusCode()).thenReturn(200);
        when(subscriptions.body()).thenReturn("{\"subscriptions\":[]}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> subscriptionCreate = mock(HttpResponse.class);
        when(subscriptionCreate.statusCode()).thenReturn(201);
        when(subscriptionCreate.body()).thenReturn(
                "{\"sid\":\"DFaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"sink_sid\":\"DGaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sinks, sinkCreate, subscriptions, subscriptionCreate);

        TwilioTemplateEventStreamStartupRunner runner =
                new TwilioTemplateEventStreamStartupRunner(true, props, http);

        var result = runner.configureOnce();

        assertEquals("DGaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.sinkSid());
        assertEquals("DFaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.subscriptionSid());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("GET", captor.getAllValues().get(0).method());
        assertEquals("POST", captor.getAllValues().get(1).method());
        assertEquals("GET", captor.getAllValues().get(2).method());
        assertEquals("POST", captor.getAllValues().get(3).method());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties props = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);
        new TwilioTemplateEventStreamStartupRunner(false, props, http).run(null);
        verifyNoInteractions(http);
    }
}
