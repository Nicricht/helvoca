package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppEmbeddedSignupTokenDebugClientTest {
    @Mock HttpClient http;

    @Test
    void debugsUserTokenWithBearerSystemUserToken() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "data": {
                    "app_id": "123456789",
                    "type": "USER",
                    "data_access_expires_at": 1800000000,
                    "expires_at": 1790000000,
                    "is_valid": true,
                    "scopes": [
                      "whatsapp_business_management",
                      "business_management"
                    ],
                    "granular_scopes": [
                      {
                        "scope": "whatsapp_business_management",
                        "target_ids": ["111111111111111", "222222222222222"]
                      }
                    ]
                  }
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupTokenDebugClient(properties, http)
                .debug("oauth token/+ sensitive", "system-user-secret");

        assertTrue(result.valid());
        assertEquals("123456789", result.appId());
        assertEquals("USER", result.type());
        assertEquals(1790000000L, result.expiresAt());
        assertEquals(1800000000L, result.dataAccessExpiresAt());
        assertEquals(
                List.of("whatsapp_business_management", "business_management"),
                result.scopes());
        assertEquals(
                List.of("111111111111111", "222222222222222"),
                result.granularTargetIds());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("GET", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/debug_token?input_token=oauth+token%2F%2B+sensitive",
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
    void rejectsMissingTokensBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        MetaWhatsAppEmbeddedSignupTokenDebugClient client =
                new MetaWhatsAppEmbeddedSignupTokenDebugClient(properties, http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.debug("", "system-token"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.debug("user-token", ""));
        verifyNoInteractions(http);
    }

    @Test
    void providerErrorsDoNotExposeBearerToken() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupTokenDebugClient(properties, http)
                        .debug("user-sensitive-token", "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup token debug failed with HTTP 401",
                error.getMessage());
        assertFalse(error.getMessage().contains("user-sensitive-token"));
        assertFalse(error.getMessage().contains("system-user-secret"));
    }
}
