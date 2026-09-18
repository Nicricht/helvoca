package cl.helvoca.messaging.outbound;

import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioWhatsAppFallbackTemplateStartupRunnerTest {
    @Test
    void createsAndSubmitsFallbackTemplateWithoutSendingMessage() throws Exception {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        props.setAuthToken("test-token");

        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> listResponse = mock(HttpResponse.class);
        when(listResponse.statusCode()).thenReturn(200);
        when(listResponse.body()).thenReturn("{\"contents\":[]}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> createResponse = mock(HttpResponse.class);
        when(createResponse.statusCode()).thenReturn(201);
        when(createResponse.body()).thenReturn(
                "{\"sid\":\"HXbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> approvalFetch = mock(HttpResponse.class);
        when(approvalFetch.statusCode()).thenReturn(404);

        @SuppressWarnings("unchecked")
        HttpResponse<String> approvalSubmit = mock(HttpResponse.class);
        when(approvalSubmit.statusCode()).thenReturn(201);
        when(approvalSubmit.body()).thenReturn(
                "{\"status\":\"received\",\"category\":\"UTILITY\",\"rejection_reason\":\"\"}");

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(listResponse, createResponse, approvalFetch, approvalSubmit);

        TwilioWhatsAppFallbackTemplateStartupRunner runner =
                new TwilioWhatsAppFallbackTemplateStartupRunner(true, 1, 5, props, http);

        var result = runner.prepareOnce();

        assertEquals("HXbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", result.contentSid());
        assertEquals("received", result.status());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("GET", captor.getAllValues().get(0).method());
        assertEquals("POST", captor.getAllValues().get(1).method());
        assertEquals("GET", captor.getAllValues().get(2).method());
        assertEquals("POST", captor.getAllValues().get(3).method());
        verifyNoMoreInteractions(http);
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        TwilioProperties props = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);
        new TwilioWhatsAppFallbackTemplateStartupRunner(false, 1, 5, props, http).run(null);
        verifyNoInteractions(http);
    }
}
