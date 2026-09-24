package cl.helvoca.messaging.audio;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiAudioTranscriptionProviderTest {

    @Test
    void retriesServerFailureThenUsesFallbackModel() throws Exception {
        GeminiLiveProperties gemini = new GeminiLiveProperties();
        gemini.setApiKey("gemini-key");
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> unavailable = mock(HttpResponse.class);
        when(unavailable.statusCode()).thenReturn(503);
        when(unavailable.body()).thenReturn("{}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> success = mock(HttpResponse.class);
        when(success.statusCode()).thenReturn(200);
        when(success.body()).thenReturn("""
                {"candidates":[{"content":{"parts":[{"text":"Audio recuperado"}]}}]}
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(unavailable, unavailable, success);

        GeminiAudioTranscriptionProvider provider = new GeminiAudioTranscriptionProvider(
                gemini,
                http,
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "https://generativelanguage.googleapis.test/v1beta");

        TranscriptionResult result = provider.transcribe(input());

        assertEquals("Audio recuperado", result.text());
        assertEquals("gemini", result.providerId());
        assertEquals("gemini-3.6-flash", result.modelId());
        assertEquals(3, result.attemptCount());
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rateLimitIsRetryableWithoutImmediateSameProviderRetry() throws Exception {
        GeminiLiveProperties gemini = new GeminiLiveProperties();
        gemini.setApiKey("gemini-key");
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> limited = mock(HttpResponse.class);
        when(limited.statusCode()).thenReturn(429);
        when(limited.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(limited);

        GeminiAudioTranscriptionProvider provider = new GeminiAudioTranscriptionProvider(
                gemini,
                http,
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "https://generativelanguage.googleapis.test/v1beta");

        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider.transcribe(input()));

        assertEquals("gemini", failure.providerId());
        assertEquals(429, failure.httpStatus());
        assertTrue(failure.retryable());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static AudioInput input() {
        return new AudioInput(
                new byte[]{1, 2, 3},
                "audio/ogg",
                "es",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "wamid.GEMINI",
                "corr-gemini");
    }
}
