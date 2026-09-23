package cl.helvoca.messaging.audio;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
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

class OpenAiAudioTranscriptionProviderTest {

    @Test
    void returnsTypedSuccessfulTranscript() throws Exception {
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        when(openAi.getApiKey()).thenReturn("openai-key");
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> success = mock(HttpResponse.class);
        when(success.statusCode()).thenReturn(200);
        when(success.body()).thenReturn("{\"text\":\"Hola desde OpenAI\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(success);

        OpenAiAudioTranscriptionProvider provider = new OpenAiAudioTranscriptionProvider(
                openAi,
                http,
                "gpt-transcribe",
                "https://api.openai.test/v1/audio/transcriptions");

        TranscriptionResult result = provider.transcribe(input());

        assertEquals("Hola desde OpenAI", result.text());
        assertEquals("openai", result.providerId());
        assertEquals("gpt-transcribe", result.modelId());
        assertEquals(1, result.attemptCount());
    }

    @Test
    void rateLimitProducesRetryableTypedFailureWithoutLeakingBody() throws Exception {
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        when(openAi.getApiKey()).thenReturn("openai-key");
        HttpClient http = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> limited = mock(HttpResponse.class);
        when(limited.statusCode()).thenReturn(429);
        when(limited.body()).thenReturn("secret-provider-body");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(limited);

        OpenAiAudioTranscriptionProvider provider = new OpenAiAudioTranscriptionProvider(
                openAi,
                http,
                "gpt-transcribe",
                "https://api.openai.test/v1/audio/transcriptions");

        AudioTranscriptionException failure = assertThrows(
                AudioTranscriptionException.class,
                () -> provider.transcribe(input()));

        assertEquals("openai", failure.providerId());
        assertEquals(429, failure.httpStatus());
        assertTrue(failure.retryable());
        assertTrue(!failure.getMessage().contains("secret-provider-body"));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static AudioInput input() {
        return new AudioInput(
                new byte[]{1, 2, 3},
                "audio/ogg",
                "es",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "wamid.OPENAI",
                "corr-openai");
    }
}
