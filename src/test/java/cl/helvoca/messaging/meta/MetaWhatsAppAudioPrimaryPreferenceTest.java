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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaWhatsAppAudioPrimaryPreferenceTest {

    @Test
    void preferredGeminiSkipsOpenAiTranscriptionRequest() throws Exception {
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

        @SuppressWarnings("unchecked")
        HttpResponse<String> geminiSuccess = mock(HttpResponse.class);
        when(geminiSuccess.statusCode()).thenReturn(200);
        when(geminiSuccess.body()).thenReturn("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Quiero reservar mañana a las cuatro"}]
                    }
                  }]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(geminiSuccess);

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
                        "https://generativelanguage.googleapis.test/v1beta",
                        true);

        String transcript = service.transcribe(businessId, "123456789012345");

        assertEquals("Quiero reservar mañana a las cuatro", transcript);
        verify(openAi, never()).getApiKey();
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var request = mockingDetails(http).getInvocations().stream()
                .filter(invocation -> "send".equals(invocation.getMethod().getName()))
                .findFirst()
                .map(invocation -> (HttpRequest) invocation.getArgument(0))
                .orElseThrow();
        assertEquals(
                "https://generativelanguage.googleapis.test/v1beta/models/gemini-3.8-flash:generateContent",
                request.uri().toString());
    }
}
