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

class MetaWhatsAppCoexistencePhoneStatusClientTest {
    private static final String PHONE_ID = "1913623884432103";
    private static final String TOKEN = "customer-access-token";

    @Test
    void isOnBusinessAppRejectsInvalidPhoneIdsAndMissingTokensBeforeHttp() {
        HttpClient http = mock(HttpClient.class);
        var client = client(http);

        assertThrows(IllegalArgumentException.class, () -> client.isOnBusinessApp(null, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> client.isOnBusinessApp("bad-id", TOKEN));
        assertThrows(IllegalStateException.class, () -> client.isOnBusinessApp(PHONE_ID, null));
        assertThrows(IllegalStateException.class, () -> client.isOnBusinessApp(PHONE_ID, "  "));

        verifyNoInteractions(http);
    }

    @Test
    void isOnBusinessAppReadsTheExactPhoneAndReturnsTheProviderFlag() throws Exception {
        HttpClient http = mock(HttpClient.class);
        respond(http, response(200, "{\"id\":\"" + PHONE_ID + "\",\"is_on_biz_app\":true}"));

        assertTrue(client(http).isOnBusinessApp(PHONE_ID, "  " + TOKEN + "  "));

        var request = captureRequest(http);
        assertEquals(URI.create("https://graph.facebook.com/v26.0/" + PHONE_ID
                + "?fields=is_on_biz_app"), request.uri());
        assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
    }

    @Test
    void isOnBusinessAppReturnsFalseWhenMetaExplicitlyReportsFalse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        respond(http, response(200, "{\"id\":\"" + PHONE_ID + "\",\"is_on_biz_app\":false}"));

        assertFalse(client(http).isOnBusinessApp(PHONE_ID, TOKEN));
    }

    @Test
    void isOnBusinessAppRejectsBothSidesOfNonSuccessHttpRange() throws Exception {
        for (int status : new int[]{199, 300}) {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(status, "{\"id\":\"" + PHONE_ID + "\",\"is_on_biz_app\":true}"));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(http).isOnBusinessApp(PHONE_ID, TOKEN));
            assertTrue(error.getMessage().contains("HTTP " + status));
        }
    }

    @Test
    void isOnBusinessAppFailsClosedForNullBodyWrongIdAndMissingFlag() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(200, null));
            client(http).isOnBusinessApp(PHONE_ID, TOKEN);
        });
        assertThrows(IllegalStateException.class, () -> {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(200, "{\"id\":\"999999999\",\"is_on_biz_app\":true}"));
            client(http).isOnBusinessApp(PHONE_ID, TOKEN);
        });
        assertThrows(IllegalStateException.class, () -> {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(200, "{\"id\":\"" + PHONE_ID + "\"}"));
            client(http).isOnBusinessApp(PHONE_ID, TOKEN);
        });
    }

    @Test
    void isOnBusinessAppRestoresInterruptAndWrapsOtherTransportFailures() throws Exception {
        HttpClient interrupted = mock(HttpClient.class);
        doThrow(new InterruptedException("provider wait interrupted"))
                .when(interrupted).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(interrupted).isOnBusinessApp(PHONE_ID, TOKEN));
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
                () -> client(failed).isOnBusinessApp(PHONE_ID, TOKEN));
        assertTrue(error.getMessage().contains("readback failed"));
    }

    @Test
    void readRejectsInvalidPhoneIdsAndMissingTokensBeforeHttp() {
        HttpClient http = mock(HttpClient.class);
        var client = client(http);

        assertThrows(IllegalArgumentException.class, () -> client.read(null, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> client.read("not-numeric", TOKEN));
        assertThrows(IllegalStateException.class, () -> client.read(PHONE_ID, null));
        assertThrows(IllegalStateException.class, () -> client.read(PHONE_ID, " "));

        verifyNoInteractions(http);
    }

    @Test
    void readReturnsVerifiedProviderStatusAndUsesTheReadOnlyFields() throws Exception {
        HttpClient http = mock(HttpClient.class);
        respond(http, response(200, "{\"id\":\"" + PHONE_ID
                + "\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\","
                + "\"display_phone_number\":\"+56950730447\"}"));

        var status = client(http).read(PHONE_ID, " " + TOKEN + " ");

        assertTrue(status.isOnBusinessApp());
        assertEquals("CLOUD_API", status.platformType());
        assertEquals("+56950730447", status.displayPhoneNumber());
        assertTrue(status.verified());

        var request = captureRequest(http);
        assertEquals(URI.create("https://graph.facebook.com/v26.0/" + PHONE_ID
                + "?fields=display_phone_number,is_on_biz_app,platform_type"), request.uri());
        assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void readReturnsAnUnverifiedStatusWhenMetaReportsBusinessAppFalse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        respond(http, response(200, "{\"id\":\"" + PHONE_ID
                + "\",\"is_on_biz_app\":false,\"platform_type\":\"CLOUD_API\","
                + "\"display_phone_number\":\"+56950730447\"}"));

        var status = client(http).read(PHONE_ID, TOKEN);

        assertFalse(status.isOnBusinessApp());
        assertFalse(status.verified());
    }

    @Test
    void readRejectsBothSidesOfNonSuccessHttpRange() throws Exception {
        for (int status : new int[]{199, 300}) {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(status, "{}"));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(http).read(PHONE_ID, TOKEN));
            assertTrue(error.getMessage().contains("HTTP " + status));
        }
    }

    @Test
    void readFailsClosedWhenAnyProviderIdentityFieldIsMissingOrMismatched() throws Exception {
        String[] bodies = {
                "{\"id\":\"999999999\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\",\"display_phone_number\":\"+56950730447\"}",
                "{\"id\":\"" + PHONE_ID + "\",\"platform_type\":\"CLOUD_API\",\"display_phone_number\":\"+56950730447\"}",
                "{\"id\":\"" + PHONE_ID + "\",\"is_on_biz_app\":true,\"display_phone_number\":\"+56950730447\"}",
                "{\"id\":\"" + PHONE_ID + "\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"}",
                null
        };
        for (String body : bodies) {
            HttpClient http = mock(HttpClient.class);
            respond(http, response(200, body));
            assertThrows(IllegalStateException.class, () -> client(http).read(PHONE_ID, TOKEN));
        }
    }

    @Test
    void readRestoresInterruptAndWrapsOtherTransportFailures() throws Exception {
        HttpClient interrupted = mock(HttpClient.class);
        doThrow(new InterruptedException("provider wait interrupted"))
                .when(interrupted).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client(interrupted).read(PHONE_ID, TOKEN));
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
                () -> client(failed).read(PHONE_ID, TOKEN));
        assertTrue(error.getMessage().contains("readback failed"));
    }

    @Test
    void statusVerificationRequiresBusinessAppCloudApiAndDisplayNumber() {
        assertTrue(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                true, "CLOUD_API", "+56950730447").verified());
        assertFalse(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                false, "CLOUD_API", "+56950730447").verified());
        assertFalse(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                true, "ON_PREMISE", "+56950730447").verified());
        assertFalse(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                true, "CLOUD_API", null).verified());
        assertFalse(new MetaWhatsAppCoexistencePhoneStatusClient.Status(
                true, "CLOUD_API", "  ").verified());
    }

    private static MetaWhatsAppCoexistencePhoneStatusClient client(HttpClient http) {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.facebook.com");
        properties.setGraphApiVersion("v26.0");
        return new MetaWhatsAppCoexistencePhoneStatusClient(properties, http);
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

    private static HttpRequest captureRequest(HttpClient http) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getValue();
    }
}
