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
class MetaWhatsAppEmbeddedSignupSharedWabaClientTest {
    @Mock HttpClient http;

    @Test
    void listsSharedWabasWithoutPuttingBearerInUrl() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "data": [
                    {
                      "id": "1906385232743451",
                      "name": "Primary WABA",
                      "currency": "USD",
                      "timezone_id": "1",
                      "message_template_namespace": "namespace_one"
                    },
                    {
                      "id": "1972385232742141",
                      "name": "Regional WABA",
                      "currency": "CLP",
                      "timezone_id": "74"
                    }
                  ],
                  "paging": {
                    "cursors": {
                      "before": "before-cursor",
                      "after": "after-cursor"
                    }
                  }
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupSharedWabaClient(properties, http)
                .list("123456789012345", "system-user-secret");

        assertEquals(2, result.wabas().size());
        assertEquals("1906385232743451", result.wabas().get(0).id());
        assertEquals("Primary WABA", result.wabas().get(0).name());
        assertEquals("USD", result.wabas().get(0).currency());
        assertEquals("1", result.wabas().get(0).timezoneId());
        assertEquals("namespace_one", result.wabas().get(0).messageTemplateNamespace());
        assertEquals("1972385232742141", result.wabas().get(1).id());
        assertNull(result.wabas().get(1).messageTemplateNamespace());
        assertEquals("after-cursor", result.afterCursor());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("GET", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/123456789012345/client_whatsapp_business_accounts",
                request.uri().toString());
        assertEquals(
                "Bearer system-user-secret",
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(
                "application/json",
                request.headers().firstValue("Accept").orElseThrow());
        assertFalse(request.uri().toString().contains("system-user-secret"));
    }

    @Test
    void requestsNextPageWithEncodedCursor() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        new MetaWhatsAppEmbeddedSignupSharedWabaClient(properties, http)
                .list("123456789012345", "system-user-secret", "abc+/=");

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        assertEquals(
                "https://graph.example.test/v99.0/123456789012345/client_whatsapp_business_accounts?after=abc%2B%2F%3D",
                requestCaptor.getValue().uri().toString());
    }

    @Test
    void rejectsInvalidInputBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var client = new MetaWhatsAppEmbeddedSignupSharedWabaClient(properties, http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.list("../other", "system-user-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.list("123456789012345", ""));
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
                () -> new MetaWhatsAppEmbeddedSignupSharedWabaClient(properties, http)
                        .list("123456789012345", "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup shared WABA lookup failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("system-user-secret"));
        assertFalse(error.getMessage().contains("provider-sensitive-body"));
    }
}
