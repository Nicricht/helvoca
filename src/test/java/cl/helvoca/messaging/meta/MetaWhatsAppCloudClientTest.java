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
class MetaWhatsAppCloudClientTest {
    @Mock HttpClient http;

    @Test
    void buildsExpectedTextMessageRequestAndReturnsMessageId() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.TEST-123\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setGraphBaseUrl("https://graph.example.test/");
        properties.setGraphApiVersion("v99.0");

        String messageId = new MetaWhatsAppCloudClient(properties, http)
                .sendText("123456789012345", "test-token", "+56933333333", "Hola desde RecepVoz");

        assertEquals("wamid.TEST-123", messageId);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertEquals("POST", request.method());
        assertEquals(
                "https://graph.example.test/v99.0/123456789012345/messages",
                request.uri().toString());
        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());

        JSONObject body = new JSONObject(bodyOf(request));
        assertEquals("whatsapp", body.getString("messaging_product"));
        assertEquals("individual", body.getString("recipient_type"));
        assertEquals("56933333333", body.getString("to"));
        assertEquals("text", body.getString("type"));
        assertFalse(body.getJSONObject("text").getBoolean("preview_url"));
        assertEquals("Hola desde RecepVoz", body.getJSONObject("text").getString("body"));
    }

    @Test
    void rejectsInvalidInputBeforeAnyNetworkCall() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        MetaWhatsAppCloudClient client = new MetaWhatsAppCloudClient(properties, http);

        assertThrows(IllegalArgumentException.class,
                () -> client.sendText("../bad", "token", "+56933333333", "Hola"));
        assertThrows(IllegalArgumentException.class,
                () -> client.sendText("1234567890", "", "+56933333333", "Hola"));
        assertThrows(IllegalArgumentException.class,
                () -> client.sendText("1234567890", "token", "not-a-number", "Hola"));
        assertThrows(IllegalArgumentException.class,
                () -> client.sendText("1234567890", "token", "+56933333333", " "));

        verifyNoInteractions(http);
    }

    @Test
    void rejectsMetaHttpErrorsWithoutLeakingResponseBody() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        var error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppCloudClient(properties, http)
                        .sendText("1234567890", "test-token", "+56933333333", "Hola"));

        assertEquals("Meta WhatsApp rejected message with HTTP 400", error.getMessage());
        assertFalse(error.getMessage().contains("sensitive"));
    }

    @Test
    void requiresProviderMessageIdOnSuccessfulResponse() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"messages\":[]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var error = assertThrows(
                IllegalStateException.class,
                () -> new MetaWhatsAppCloudClient(properties, http)
                        .sendText("1234567890", "test-token", "+56933333333", "Hola"));

        assertEquals("Meta WhatsApp did not return a message id", error.getMessage());
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
