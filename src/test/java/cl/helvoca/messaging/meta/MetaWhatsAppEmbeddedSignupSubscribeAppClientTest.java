package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppEmbeddedSignupSubscribeAppClientTest {
    @Mock HttpClient http;

    @Test
    void subscribesAppWithBearerAndNoRequestBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupSubscribeAppClient(properties, http)
                .subscribe(
                        "1906385232743451",
                        "system-user-secret");

        assertTrue(result.success());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/1906385232743451/subscribed_apps",
                request.uri().toString());
        assertEquals(
                "Bearer system-user-secret",
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(
                "application/json",
                request.headers().firstValue("Accept").orElseThrow());
        assertTrue(request.bodyPublisher().isPresent());
        assertEquals(
                0L,
                request.bodyPublisher().orElseThrow().contentLength());
        assertFalse(request.uri().toString().contains("system-user-secret"));
    }

    @Test
    void acceptsStringTrueSuccessFromMetaExample() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":\"true\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var result = new MetaWhatsAppEmbeddedSignupSubscribeAppClient(properties, http)
                .subscribe(
                        "1906385232743451",
                        "system-user-secret");

        assertTrue(result.success());
    }

    @Test
    void rejectsInvalidWabaIdAndMissingBearerBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var client = new MetaWhatsAppEmbeddedSignupSubscribeAppClient(properties, http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.subscribe("../waba", "system-user-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.subscribe("1906385232743451", ""));

        verifyNoInteractions(http);
    }

    @Test
    void providerErrorsDoNotExposeBearerOrResponseBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupSubscribeAppClient(properties, http)
                        .subscribe(
                                "1906385232743451",
                                "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup app subscription failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("system-user-secret"));
        assertFalse(error.getMessage().contains("provider-sensitive-body"));
    }

    @Test
    void requiresExplicitSuccessFlag() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupSubscribeAppClient(properties, http)
                        .subscribe(
                                "1906385232743451",
                                "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup app subscription returned no success flag",
                error.getMessage());
    }
}
