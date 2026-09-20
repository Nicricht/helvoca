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
class MetaWhatsAppEmbeddedSignupAssignSystemUserClientTest {
    @Mock HttpClient http;

    @Test
    void assignsSystemUserWithAdminBearerAndManageTask() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupAssignSystemUserClient(properties, http)
                .assign(
                        "1906385232743451",
                        "998877665544332",
                        "manage",
                        "admin-system-user-secret");

        assertTrue(result.success());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/1906385232743451/assigned_users?user=998877665544332&tasks=%5B%27MANAGE%27%5D",
                request.uri().toString());
        assertEquals(
                "Bearer admin-system-user-secret",
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(
                "application/json",
                request.headers().firstValue("Accept").orElseThrow());
        assertFalse(request.uri().toString().contains("admin-system-user-secret"));
    }

    @Test
    void supportsDevelopTask() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var result = new MetaWhatsAppEmbeddedSignupAssignSystemUserClient(properties, http)
                .assign(
                        "1906385232743451",
                        "998877665544332",
                        "DEVELOP",
                        "admin-system-user-secret");

        assertTrue(result.success());
    }

    @Test
    void rejectsInvalidInputsBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var client = new MetaWhatsAppEmbeddedSignupAssignSystemUserClient(properties, http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.assign("../waba", "998877665544332", "MANAGE", "admin-token"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.assign("1906385232743451", "../user", "MANAGE", "admin-token"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.assign("1906385232743451", "998877665544332", "OWNER", "admin-token"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.assign("1906385232743451", "998877665544332", "MANAGE", ""));

        verifyNoInteractions(http);
    }

    @Test
    void providerErrorsDoNotExposeAdminBearerOrResponseBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupAssignSystemUserClient(properties, http)
                        .assign(
                                "1906385232743451",
                                "998877665544332",
                                "MANAGE",
                                "admin-system-user-secret"));

        assertEquals(
                "Meta Embedded Signup system user assignment failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("admin-system-user-secret"));
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
                () -> new MetaWhatsAppEmbeddedSignupAssignSystemUserClient(properties, http)
                        .assign(
                                "1906385232743451",
                                "998877665544332",
                                "MANAGE",
                                "admin-system-user-secret"));

        assertEquals(
                "Meta Embedded Signup system user assignment returned no success flag",
                error.getMessage());
    }
}
