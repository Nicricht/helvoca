package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaWhatsAppEmbeddedSignupWabaAccessClientTest {
    private static final String WABA_ID = "1906385232743451";
    private static final String TOKEN = "customer-short-lived-token";

    @Test
    void rejectsInvalidWabaIdsAndMissingCustomerTokensBeforeHttp() {
        HttpClient http = mock(HttpClient.class);
        var client = client(http);

        assertThrows(IllegalArgumentException.class, () -> client.requireAccess(null, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> client.requireAccess("not-numeric", TOKEN));
        assertThrows(IllegalStateException.class, () -> client.requireAccess(WABA_ID, null));
        assertThrows(IllegalStateException.class, () -> client.requireAccess(WABA_ID, "  "));

        verifyNoInteractions(http);
    }

    @Test
    void verifiesTheExactCustomerWabaUsingTheCustomerToken() throws Exception {
        HttpClient http = mock(HttpClient.class);
        respond(http, response(200, "{\"id\":\"" + WABA_ID + "\"}"));

        assertDoesNotThrow(() -> client(http).requireAccess(" " + WABA_ID + " ", " " + TOKEN + " "));

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(URI.create("https://graph.facebook.com/v26.0/" + WABA_ID + "?fields=id"),
                captor.getValue().uri());
        assertEquals("Bearer " + TOKEN,
                captor.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json",
                captor.getValue().headers().firstValue("Accept").orElseThrow());
    }

    @Test
    void rejectsBothSidesOfNonSuccessHttpRange() throws Exception {
        for (int status : new int[]{199, 300}) {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(status, "{\"id\":\"" + WABA_ID + "\"}"));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(http).requireAccess(WABA_ID, TOKEN));
            assertTrue(error.getMessage().contains("HTTP " + status));
        }
    }

    @Test
    void rejectsNullBodyAndMismatchedWabaReadback() throws Exception {
        HttpClient nullBody = mock(HttpClient.class);
        respond(nullBody, response(200, null));
        assertThrows(IllegalStateException.class,
                () -> client(nullBody).requireAccess(WABA_ID, TOKEN));

        HttpClient mismatch = mock(HttpClient.class);
        respond(mismatch, response(200, "{\"id\":\"1999999999999999\"}"));
        assertThrows(IllegalStateException.class,
                () -> client(mismatch).requireAccess(WABA_ID, TOKEN));
    }

    @Test
    void restoresInterruptAndWrapsOtherTransportFailures() throws Exception {
        HttpClient interrupted = mock(HttpClient.class);
        doThrow(new InterruptedException("provider wait interrupted"))
                .when(interrupted).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(interrupted).requireAccess(WABA_ID, TOKEN));
            assertTrue(error.getMessage().contains("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        HttpClient failed = mock(HttpClient.class);
        doThrow(new IOException("network unavailable"))
                .when(failed).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client(failed).requireAccess(WABA_ID, TOKEN));
        assertTrue(error.getMessage().contains("read failed"));
    }

    private static MetaWhatsAppEmbeddedSignupWabaAccessClient client(HttpClient http) {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.facebook.com");
        properties.setGraphApiVersion("v26.0");
        return new MetaWhatsAppEmbeddedSignupWabaAccessClient(properties, http);
    }

    private static HttpResponse<String> response(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static void respond(HttpClient http, HttpResponse<String> response) throws Exception {
        doReturn(response).when(http).send(
                any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
