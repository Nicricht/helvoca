package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwilioDiagnosticStartupProbeTest {

    @Test
    void canBeConstructedBySpringWhenDiagnosticIsDisabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TwilioProperties.class);
            context.register(TwilioDiagnosticStartupProbe.class);
            context.refresh();

            assertFalse(context.getBean(TwilioDiagnosticStartupProbe.class).probe().success());
        }
    }

    @Test
    void reportsNotConfiguredWithoutCallingTwilio() throws Exception {
        TwilioProperties properties = new TwilioProperties();
        HttpClient http = mock(HttpClient.class);

        var probe = new TwilioDiagnosticStartupProbe(properties, true, http);
        var result = probe.probe();

        assertFalse(result.success());
        assertEquals("NOT_CONFIGURED", result.code());
        verifyNoInteractions(http);
    }

    @Test
    void validatesCredentialsWithReadOnlyAccountGet() throws Exception {
        TwilioProperties properties = configuredProperties();
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"active\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var probe = new TwilioDiagnosticStartupProbe(properties, true, http);
        var result = probe.probe();

        assertTrue(result.success());
        assertEquals("OK", result.code());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("GET", request.method());
        assertEquals("https://api.twilio.com/2010-04-01/Accounts/ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.json",
                request.uri().toString());
        assertTrue(request.headers().firstValue("Authorization").orElse("").startsWith("Basic "));
    }

    @Test
    void reportsProviderAuthenticationFailureWithoutCreatingCalls() throws Exception {
        TwilioProperties properties = configuredProperties();
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"message\":\"Authenticate\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var probe = new TwilioDiagnosticStartupProbe(properties, true, http);
        var result = probe.probe();

        assertFalse(result.success());
        assertEquals("AUTH_FAILED", result.code());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sanitizesTransportFailuresWithoutExposingCredentials() throws Exception {
        TwilioProperties properties = configuredProperties();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException(
                        "connection failed for ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa using test-token"));

        var probe = new TwilioDiagnosticStartupProbe(properties, true, http);
        var result = probe.probe();

        assertFalse(result.success());
        assertEquals("NETWORK_ERROR", result.code());
        assertFalse(result.detail().contains("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(result.detail().contains("test-token"));
        assertEquals("Twilio account API could not be reached", result.detail());
    }

    private static TwilioProperties configuredProperties() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        properties.setAuthToken("test-token");
        properties.setPublicBaseUrl("https://helvoca.example");
        return properties;
    }
}
