package cl.helvoca.messaging.audio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeepgramAudioTranscriptionProviderTest {
    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/listen";
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsOriginalAudioAndParsesTranscript() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/v1/listen", exchange -> {
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "{\"results\":{\"channels\":[{\"alternatives\":[{\"transcript\":\"Quiero reservar mañana\"}]}]}}");
        });

        DeepgramAudioTranscriptionProvider provider = provider(2);
        TranscriptionResult result = provider.transcribe(input());

        assertEquals("Quiero reservar mañana", result.text());
        assertEquals("deepgram", result.providerId());
        assertEquals("nova-3", result.modelId());
        assertEquals("POST", method.get());
        assertEquals("model=nova-3&smart_format=true&language=multi", query.get());
        assertEquals("Token test-key", auth.get());
        assertEquals("audio/ogg", contentType.get());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, body.get());
    }

    @Test
    void maps429AsRetryable() {
        server.createContext("/v1/listen", exchange -> respond(exchange, 429, "rate limited secret body"));
        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider(2).transcribe(input()));
        assertEquals(429, failure.httpStatus());
        assertTrue(failure.retryable());
        assertFalse(failure.getMessage().contains("secret body"));
    }

    @Test
    void maps503AsRetryable() {
        server.createContext("/v1/listen", exchange -> respond(exchange, 503, "unavailable"));
        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider(2).transcribe(input()));
        assertEquals(503, failure.httpStatus());
        assertTrue(failure.retryable());
    }

    @Test
    void maps401AsPermanent() {
        server.createContext("/v1/listen", exchange -> respond(exchange, 401, "bad token"));
        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider(2).transcribe(input()));
        assertEquals(401, failure.httpStatus());
        assertFalse(failure.retryable());
    }

    @Test
    void rejectsBlankTranscriptAsPermanent() {
        server.createContext("/v1/listen", exchange -> respond(exchange, 200, "{\"results\":{\"channels\":[{\"alternatives\":[{\"transcript\":\"   \"}]}]}}"));
        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider(2).transcribe(input()));
        assertFalse(failure.retryable());
    }

    @Test
    void timeoutIsRetryable() {
        server.createContext("/v1/listen", exchange -> {
            try {
                Thread.sleep(1500);
                respond(exchange, 200, "{\"results\":{\"channels\":[{\"alternatives\":[{\"transcript\":\"late\"}]}]}}"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider(1).transcribe(input()));
        assertTrue(failure.retryable());
    }

    private DeepgramAudioTranscriptionProvider provider(int timeoutSeconds) {
        WhatsAppAudioTranscriptionProperties properties = new WhatsAppAudioTranscriptionProperties();
        properties.setDeepgramEnabled(true);
        properties.setDeepgramApiKey("test-key");
        properties.setDeepgramEndpoint(endpoint);
        properties.setDeepgramModel("nova-3");
        properties.setDeepgramLanguage("multi");
        properties.setDeepgramTimeoutSeconds(timeoutSeconds);
        return new DeepgramAudioTranscriptionProvider(properties, HttpClient.newHttpClient());
    }

    private static AudioInput input() {
        return new AudioInput(
                new byte[]{1, 2, 3, 4},
                "audio/ogg; codecs=opus",
                "",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "wamid.DEEPGRAM",
                "22222222-2222-2222-2222-222222222222");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
