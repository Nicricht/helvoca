package cl.helvoca.messaging;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiMessagingAiFallbackTest {

    @Test
    void returnsSanitizedGeminiTextWithoutPuttingApiKeyInUrl() throws Exception {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setApiKey("secret-key");

        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {"text": "Hola **cliente**"}
                        ]
                      }
                    }
                  ]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GeminiMessagingAiFallback fallback = new GeminiMessagingAiFallback(
                properties,
                "gemini-3.8-flash",
                "https://example.test/v1beta",
                http);

        String answer = fallback.respond(
                "Responde en español.",
                List.of(new MessagingAiClient.Turn("user", "Hola")),
                Set.of(),
                (name, args) -> "{}");

        assertEquals("Hola cliente", answer);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();

        assertEquals(
                "https://example.test/v1beta/models/gemini-3.8-flash:generateContent",
                request.uri().toString());
        assertFalse(request.uri().toString().contains("secret-key"));
        assertEquals("secret-key", request.headers().firstValue("x-goog-api-key").orElseThrow());
    }

    @Test
    void recognizesNestedOpenAiRateLimit() {
        RuntimeException wrapped = new RuntimeException(new RateLimitException());
        assertTrue(OpenAiMessagingAiClient.isRateLimit(wrapped));
        assertFalse(OpenAiMessagingAiClient.isRateLimit(new IllegalStateException("other")));
    }

    private static final class RateLimitException extends RuntimeException {
    }
}
