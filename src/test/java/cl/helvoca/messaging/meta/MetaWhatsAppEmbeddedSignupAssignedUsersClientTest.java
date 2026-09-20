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
class MetaWhatsAppEmbeddedSignupAssignedUsersClientTest {
    @Mock HttpClient http;

    @Test
    void fetchesAssignedUsersWithProviderBusinessFilter() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "data": [
                    {
                      "id": "998877665544332",
                      "name": "RecepVoz System User",
                      "tasks": ["MANAGE", "DEVELOP"]
                    }
                  ]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupAssignedUsersClient(properties, http)
                .fetch(
                        "1906385232743451",
                        "112233445566778",
                        "system-user-secret");

        assertTrue(result.hasAssignedUsers());
        assertEquals(1, result.users().size());
        assertEquals("998877665544332", result.users().get(0).id());
        assertEquals("RecepVoz System User", result.users().get(0).name());
        assertEquals(List.of("MANAGE", "DEVELOP"), result.users().get(0).tasks());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("GET", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/1906385232743451/assigned_users?business=112233445566778",
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
    void acceptsEmptyAssignedUserList() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"data\":[]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var result = new MetaWhatsAppEmbeddedSignupAssignedUsersClient(properties, http)
                .fetch(
                        "1906385232743451",
                        "112233445566778",
                        "system-user-secret");

        assertFalse(result.hasAssignedUsers());
        assertTrue(result.users().isEmpty());
    }

    @Test
    void rejectsInvalidIdsAndMissingBearerBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var client = new MetaWhatsAppEmbeddedSignupAssignedUsersClient(properties, http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("../waba", "112233445566778", "system-user-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("1906385232743451", "../business", "system-user-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("1906385232743451", "112233445566778", ""));

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
                () -> new MetaWhatsAppEmbeddedSignupAssignedUsersClient(properties, http)
                        .fetch(
                                "1906385232743451",
                                "112233445566778",
                                "system-user-secret"));

        assertEquals(
                "Meta Embedded Signup assigned users lookup failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("system-user-secret"));
        assertFalse(error.getMessage().contains("provider-sensitive-body"));
    }
}
