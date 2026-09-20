package cl.helvoca.messaging.meta;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppEmbeddedSignupRegisterPhoneClientTest {
    @Mock HttpClient http;

    @Test
    void registersPhoneWithBearerAndSixDigitPinInJsonBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        var result = new MetaWhatsAppEmbeddedSignupRegisterPhoneClient(properties, http)
                .register("1913623884432103", "token-value", "123456");

        assertTrue(result.success());

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/1913623884432103/register",
                request.uri().toString());
        assertEquals(
                "Bearer token-value",
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(
                "application/json",
                request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(
                "application/json",
                request.headers().firstValue("Accept").orElseThrow());

        JSONObject body = new JSONObject(bodyOf(request));
        assertEquals("whatsapp", body.getString("messaging_product"));
        assertEquals("123456", body.getString("pin"));
        assertFalse(request.uri().toString().contains("token-value"));
        assertFalse(request.uri().toString().contains("123456"));
    }

    @Test
    void acceptsStringTrueSuccessFromMetaExample() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":\"true\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var result = new MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
                new MetaWhatsAppProperties(),
                http)
                .register("1913623884432103", "token-value", "123456");

        assertTrue(result.success());
    }

    @Test
    void rejectsInvalidInputsBeforeNetworkCall() {
        var client = new MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
                new MetaWhatsAppProperties(),
                http);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.register("../phone", "token-value", "123456"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.register("1913623884432103", "", "123456"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.register("1913623884432103", "token-value", "12345"));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.register("1913623884432103", "token-value", "12A456"));

        verifyNoInteractions(http);
    }

    @Test
    void providerErrorsDoNotExposeBearerPinOrResponseBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
                        new MetaWhatsAppProperties(),
                        http)
                        .register(
                                "1913623884432103",
                                "token-value",
                                "123456"));

        assertEquals(
                "Meta Embedded Signup phone registration failed with HTTP 403",
                error.getMessage());
        assertFalse(error.getMessage().contains("token-value"));
        assertFalse(error.getMessage().contains("123456"));
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

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupRegisterPhoneClient(
                        new MetaWhatsAppProperties(),
                        http)
                        .register(
                                "1913623884432103",
                                "token-value",
                                "123456"));

        assertEquals(
                "Meta Embedded Signup phone registration returned no success flag",
                error.getMessage());
    }

    private static String bodyOf(HttpRequest request) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(output.toString(StandardCharsets.UTF_8));
            }
        });
        return body.join();
    }
}
