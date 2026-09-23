package cl.helvoca.messaging.meta;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaWhatsAppAudioTranscriptionServiceTest {

    @Test
    void downloadsTenantMediaAndReturnsTranscriptText() throws Exception {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppCloudClient meta = mock(MetaWhatsAppCloudClient.class);
        MetaWhatsAppAccessTokenResolver tokens = mock(MetaWhatsAppAccessTokenResolver.class);
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        HttpClient http = mock(HttpClient.class);

        when(tokens.resolve(businessId)).thenReturn(Optional.of("meta-token"));
        when(meta.downloadMedia("123456789012345", "meta-token"))
                .thenReturn(new MetaWhatsAppCloudClient.DownloadedMedia(
                        new byte[]{1, 2, 3, 4},
                        "audio/ogg; codecs=opus"));
        when(openAi.getApiKey()).thenReturn("openai-key");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"text\":\"Quiero reservar mañana a las cuatro\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MetaWhatsAppAudioTranscriptionService service =
                new MetaWhatsAppAudioTranscriptionService(
                        meta,
                        tokens,
                        openAi,
                        http,
                        "gpt-transcribe",
                        "https://api.openai.test/v1/audio/transcriptions");

        String transcript = service.transcribe(businessId, "123456789012345");

        assertEquals("Quiero reservar mañana a las cuatro", transcript);
        verify(meta).downloadMedia("123456789012345", "meta-token");

        var request = mockingDetails(http).getInvocations().stream()
                .filter(invocation -> "send".equals(invocation.getMethod().getName()))
                .findFirst()
                .map(invocation -> (HttpRequest) invocation.getArgument(0))
                .orElseThrow();

        assertEquals("https://api.openai.test/v1/audio/transcriptions", request.uri().toString());
        assertEquals("Bearer openai-key",
                request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(request.headers().firstValue("Content-Type").orElseThrow()
                .startsWith("multipart/form-data; boundary="));
    }
    @Test
    void fallsBackToGeminiWhenOpenAiTranscriptionFails() throws Exception {
        UUID businessId = UUID.randomUUID();
        MetaWhatsAppCloudClient meta = mock(MetaWhatsAppCloudClient.class);
        MetaWhatsAppAccessTokenResolver tokens = mock(MetaWhatsAppAccessTokenResolver.class);
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        GeminiLiveProperties gemini = new GeminiLiveProperties();
        gemini.setApiKey("gemini-key");
        HttpClient http = mock(HttpClient.class);

        when(tokens.resolve(businessId)).thenReturn(Optional.of("meta-token"));
        when(meta.downloadMedia("123456789012345", "meta-token"))
                .thenReturn(new MetaWhatsAppCloudClient.DownloadedMedia(
                        new byte[]{1, 2, 3, 4},
                        "audio/ogg; codecs=opus"));
        when(openAi.getApiKey()).thenReturn("openai-key");

        @SuppressWarnings("unchecked")
        HttpResponse<String> openAiFailure = mock(HttpResponse.class);
        when(openAiFailure.statusCode()).thenReturn(429);
        when(openAiFailure.body()).thenReturn("{}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> geminiSuccess = mock(HttpResponse.class);
        when(geminiSuccess.statusCode()).thenReturn(200);
        when(geminiSuccess.body()).thenReturn("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Hola, quiero reservar hoy a las cuatro."}]
                    }
                  }]
                }
                """);

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(openAiFailure, geminiSuccess);

        MetaWhatsAppAudioTranscriptionService service =
                new MetaWhatsAppAudioTranscriptionService(
                        meta,
                        tokens,
                        openAi,
                        gemini,
                        http,
                        "gpt-transcribe",
                        "https://api.openai.test/v1/audio/transcriptions",
                        "gemini-3.8-flash",
                        "https://generativelanguage.googleapis.test/v1beta");

        String transcript = service.transcribe(businessId, "123456789012345");

        assertEquals("Hola, quiero reservar hoy a las cuatro.", transcript);
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var requests = mockingDetails(http).getInvocations().stream()
                .filter(invocation -> "send".equals(invocation.getMethod().getName()))
                .map(invocation -> (HttpRequest) invocation.getArgument(0))
                .toList();

        assertEquals("https://api.openai.test/v1/audio/transcriptions",
                requests.get(0).uri().toString());
        assertEquals(
                "https://generativelanguage.googleapis.test/v1beta/models/gemini-3.8-flash:generateContent",
                requests.get(1).uri().toString());
        assertEquals("gemini-key",
                requests.get(1).headers().firstValue("x-goog-api-key").orElseThrow());
    }

}
