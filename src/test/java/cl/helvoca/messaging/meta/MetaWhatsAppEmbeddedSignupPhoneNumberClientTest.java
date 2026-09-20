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
class MetaWhatsAppEmbeddedSignupPhoneNumberClientTest {
    @Mock HttpClient http;

    @Test
    void listsPhoneNumbersWithoutPuttingBearerInUrl() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "data": [
                    {
                      "verified_name": "RecepVoz Demo",
                      "display_phone_number": "+56 9 1111 2222",
                      "id": "1906385232743451",
                      "quality_rating": "GREEN",
                      "code_verification_status": "VERIFIED"
                    },
                    {
                      "verified_name": "RecepVoz Backup",
                      "display_phone_number": "+56 9 3333 4444",
                      "id": "1913623884432103",
                      "quality_rating": "NA"
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

        var result = new MetaWhatsAppEmbeddedSignupPhoneNumberClient(properties, http)
                .list("123456789012345", "system-user-secret");

        assertEquals(2, result.phoneNumbers().size());
        assertEquals("1906385232743451", result.phoneNumbers().get(0).id());
        assertEquals("+56 9 1111 2222", result.phoneNumbers().get(0).displayPhoneNumber());
        assertEquals("RecepVoz Demo", result.phoneNumbers().get(0).verifiedName());
        assertEquals("GREEN", result.phoneNumbers().get(0).qualityRating());
        assertEquals("VERIFIED", result.phoneNumbers().get(0).codeVerificationStatus());
        assertNull(result.phoneNumbers().get(1).codeVerificationStatus());
        assertEquals("after-cursor", result.afterCursor());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("GET", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/123456789012345/phone_numbers",
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

        new MetaWhatsAppEmbeddedSignupPhoneNumberClient(properties, http)
                .list("123456789012345", "system-user-secret", "abc+/=");

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        assertEquals(
                "https://graph.example.test/v99.0/123456789012345/phone_numbers?after=abc%2B%2F%3D",
                requestCaptor.getValue().uri().toString());
    }

    @Test
    void rejectsInvalidInputBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var client = new MetaWhatsAppEmbeddedSignupPhoneNumberClient(properties, http);

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
                () -> new MetaWhatsAppEmbeddedSignupPhoneNumberClient(properties, http)
                        .list("123456789012345", "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup phone number lookup failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("system-user-secret"));
    }

    @Test
    void rejectsResponseWithoutData() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupPhoneNumberClient(properties, http)
                        .list("123456789012345", "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup phone number lookup returned no data",
                error.getMessage());
    }
}
