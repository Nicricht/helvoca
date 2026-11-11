package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepgramEconomyVoiceSessionTest {

    @Test
    void interimResultsCannotTriggerRepliesAndFinalSegmentsAreJoinedBeforeGeminiFallback() {
        Fixture f = new Fixture();
        when(f.gemini.configuredFor(f.context.destinationNumber())).thenReturn(true);
        when(f.gemini.createSession(eq(f.context), eq(f.transport))).thenReturn(f.geminiSession);
        f.session.consumeTranscript(packet("Quiero reservar", false, false));
        verifyNoInteractions(f.gemini, f.tools);
        f.session.consumeTranscript(packet("Quiero reservar", true, false));
        verifyNoInteractions(f.gemini, f.tools);
        f.session.consumeTranscript(packet("mañana", true, true));
        verify(f.gemini).createSession(eq(f.context), eq(f.transport));
        verify(f.geminiSession).start();
        verifyNoInteractions(f.tools);
        verifyNoInteractions(f.http);
        f.session.consumeTranscript(packet("algo más", true, true));
        verify(f.geminiSession, times(1)).start();
    }

    @Test
    void verifiedTenantPriceUsesRawPcmuAudioForTwilioWithoutExternalNetwork() {
        Fixture f = new Fixture();
        when(f.transport.isOpen()).thenReturn(true);
        when(f.tools.execute(eq(f.context), eq("list_services"), eq("{}"))).thenReturn(
                new JSONObject().put("success", true).put("data",
                        new JSONObject().put("services", new JSONArray()
                                .put(new JSONObject().put("name", "Psicología").put("price", 35000))))
                        .toString());
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[]{0, 1, 2, 3});
        when(f.http.sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        f.session.consumeTranscript(packet("¿Cuánto cuesta la consulta de psicología?", true, true));

        verify(f.tools).execute(eq(f.context), eq("list_services"), eq("{}"));
        verify(f.transport).clearPlayback(f.context.streamSid());
        verify(f.transport).sendAudio(f.context.streamSid(), "AAECAw==");
        verifyNoInteractions(f.gemini);
        verify(f.http, times(1)).sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void failingTenantGreetingHandsOffToGeminiOnceWithoutSendingTtsOrHangingUp() {
        Fixture f = new Fixture();
        java.net.http.WebSocket socket = mock(java.net.http.WebSocket.class);
        when(f.gemini.configuredFor(f.context.destinationNumber())).thenReturn(true);
        when(f.gemini.createSession(eq(f.context), eq(f.transport))).thenReturn(f.geminiSession);
        when(f.tools.agentGreeting(eq(f.context), anyString()))
                .thenThrow(new IllegalStateException("tenant settings unavailable"));

        assertDoesNotThrow(() -> f.session.onOpen(socket));
        verify(socket).request(1);
        verify(socket).abort();
        verify(f.gemini).createSession(eq(f.context), eq(f.transport));
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
        verify(f.transport, never()).closeOnUpstreamFailure();
        f.session.onError(socket, new IllegalStateException("late provider callback"));
        verify(f.geminiSession, times(1)).start();
    }

    @Test
    void upstreamFailureWithoutGeminiClosesCarrierExactlyOnce() {
        Fixture f = new Fixture();
        when(f.gemini.configuredFor(f.context.destinationNumber())).thenReturn(false);
        f.session.onError(null, new IllegalStateException("provider unavailable"));
        f.session.onError(null, new IllegalStateException("duplicate event"));
        verify(f.transport, times(1)).closeOnUpstreamFailure();
        verify(f.gemini, never()).createSession(any(), any());
        verifyNoInteractions(f.http, f.tools);
    }

    @Test
    void closedSessionNeverEscalatesOrSynthesizes() {
        Fixture f = new Fixture();
        f.session.close();
        f.session.consumeTranscript(packet("Quiero reservar", true, true));
        f.session.onError(null, new IllegalStateException("late callback"));
        verifyNoInteractions(f.gemini, f.http, f.tools);
        verify(f.transport, never()).closeOnUpstreamFailure();
    }

    private static JSONObject packet(String text, boolean isFinal, boolean speechFinal) {
        return new JSONObject().put("type", "Results")
                .put("is_final", isFinal).put("speech_final", speechFinal)
                .put("channel", new JSONObject().put("alternatives",
                        new JSONArray().put(new JSONObject().put("transcript", text))));
    }

    private static final class Fixture {
        final RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null, "+56944445555",
                "+56911112222", "MZ-test-stream");
        final DeepgramEconomyVoiceProperties settings = new DeepgramEconomyVoiceProperties();
        final VoiceTransportSession transport = mock(VoiceTransportSession.class);
        final RealtimeToolService tools = mock(RealtimeToolService.class);
        final GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        final VoiceAiSession geminiSession = mock(VoiceAiSession.class);
        final HttpClient http = mock(HttpClient.class);
        final DeepgramEconomyVoiceSession session = new DeepgramEconomyVoiceSession(
                context, transport, settings,
                new DeepgramEconomyPublicResponder(tools), tools, gemini, http);
    }
}
