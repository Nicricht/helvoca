package cl.helvoca.messaging;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

class OpenAiMessagingAiClientProviderPreferenceTest {

    @Test
    void preferredGeminiSkipsOpenAiAttemptCompletely() {
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        GeminiMessagingAiFallback gemini = mock(GeminiMessagingAiFallback.class);
        when(gemini.configured()).thenReturn(true);
        when(gemini.respond(any(), any(), anySet(), any()))
                .thenReturn("Respuesta directa de Gemini");

        OpenAiMessagingAiClient client = new OpenAiMessagingAiClient(openAi, gemini, true);

        String answer = client.respond(
                "Responde en español.",
                List.of(new MessagingAiClient.Turn("user", "Hola")),
                Set.of(),
                (name, args) -> "{}");

        assertEquals("Respuesta directa de Gemini", answer);
        verify(gemini).respond(any(), any(), anySet(), any());
        verify(openAi, never()).hasApiKey();
    }
    @Test
    void preferredGeminiFallsBackToOpenAiWhenProviderIsUnavailableBeforeTools() {
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        when(openAi.hasApiKey()).thenReturn(true);

        GeminiMessagingAiFallback gemini = mock(GeminiMessagingAiFallback.class);
        when(gemini.configured()).thenReturn(true);
        when(gemini.respond(any(), any(), anySet(), any()))
                .thenThrow(new GeminiMessagingAiFallback.ProviderUnavailableException("HTTP 429"));

        OpenAiMessagingAiClient client = spy(new OpenAiMessagingAiClient(openAi, gemini, true));
        doReturn("Respuesta de OpenAI")
                .when(client)
                .respondWithOpenAi(any(), any(), anySet(), any());

        String answer = client.respond(
                "Responde en español.",
                List.of(new MessagingAiClient.Turn("user", "Sí")),
                Set.of(),
                (name, args) -> "{}");

        assertEquals("Respuesta de OpenAI", answer);
        verify(gemini).respond(any(), any(), anySet(), any());
        verify(client).respondWithOpenAi(any(), any(), anySet(), any());
    }

}
