package cl.helvoca.messaging.meta;

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
class MetaWhatsAppEmbeddedSignupTokenExchangeClientTest {
    @Mock HttpClient http;

    @Test
    void exchangesCodeServerSideAndRedactsReturnedToken() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "access_token":"EAATEST-sensitive-token",
                  "token_type":"bearer",
                  "expires_in":3600
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");
        properties.setEmbeddedSignupAppId("123456789");
        properties.setAppSecret("app-secret-value");

        MetaWhatsAppEmbeddedSignupToken result =
                new MetaWhatsAppEmbeddedSignupTokenExchangeClient(properties, http)
                        .exchange("temporary code/+");

        assertEquals("EAATEST-sensitive-token", result.accessToken());
        assertEquals("bearer", result.tokenType());
        assertEquals(3600L, result.expiresInSeconds());
        assertFalse(result.toString().contains("EAATEST-sensitive-token"));

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/oauth/access_token",
                request.uri().toString());
        assertEquals(
                "application/x-www-form-urlencoded",
                request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("application/json",
                request.headers().firstValue("Accept").orElseThrow());

        String body = bodyOf(request);
        assertTrue(body.contains("client_id=123456789"));
        assertTrue(body.contains("client_secret=app-secret-value"));
        assertTrue(body.contains("code=temporary+code%2F%2B"));
        assertFalse(request.uri().toString().contains("app-secret-value"));
        assertFalse(request.uri().toString().contains("temporary"));
    }

    @Test
    void rejectsMissingConfigurationBeforeNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        MetaWhatsAppEmbeddedSignupTokenExchangeClient client =
                new MetaWhatsAppEmbeddedSignupTokenExchangeClient(properties, http);

        assertThrows(IllegalArgumentException.class,
                () -> client.exchange("temporary-code"));
        verifyNoInteractions(http);
    }

    @Test
    void providerErrorsNeverLeakResponseBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn(
                """\n                {"error":{"message":"sensitive-code-or-secret-must-not-leak"}}\n                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEmbeddedSignupAppId("123456789");
        properties.setAppSecret("app-secret-value");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppEmbeddedSignupTokenExchangeClient(properties, http)
                        .exchange("temporary-sensitive-code"));

        assertEquals(
                "Meta Embedded Signup token exchange failed with HTTP 400",
                error.getMessage());
        assertFalse(error.getMessage().contains("sensitive-code-or-secret-must-not-leak"));
        assertFalse(error.getMessage().contains("temporary-sensitive-code"));
        assertFalse(error.getMessage().contains("app-secret-value"));
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
