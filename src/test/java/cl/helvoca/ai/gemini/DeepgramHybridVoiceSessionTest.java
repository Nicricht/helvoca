package cl.helvoca.ai.gemini;

import cl.helvoca.ai.deepgram.DeepgramVoiceProperties;
import cl.helvoca.ai.deepgram.EconomicVoiceFactsService;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepgramHybridVoiceSessionTest {
    @Test
    void publicHoursUseTenantFactsAndDeepgramTtsWithoutStartingGemini() {
        Fixture f = new Fixture();
        when(f.facts.answer(f.context, "¿Cuál es el horario de atención?"))
                .thenReturn(Optional.of("Atendemos de lunes a viernes de 08:00 a 20:00."));
        DeepgramHybridVoiceSession session = f.session();

        session.start();
        session.onOpen(f.socket);
        session.onText(f.socket, results("¿Cuál es el horario de atención?"), true);

        verify(f.facts).answer(f.context, "¿Cuál es el horario de atención?");
        verify(f.gemini, never()).createTextContinuationSession(any(), any(), any());
        verify(f.transcripts).append(f.context.callId(), "USER", "¿Cuál es el horario de atención?");
        verify(f.transcripts).append(f.context.callId(), "ASSISTANT",
                "Atendemos de lunes a viernes de 08:00 a 20:00.");
        verify(f.transport, atLeast(2)).sendAudio(eq(f.context.streamSid()), anyString());

        session.close();
    }

    @Test
    void complexRequestIsForwardedAsTextToGeminiAndDoesNotUseRulesAnswer() {
        Fixture f = new Fixture();
        GeminiLiveVoiceSession fallback = mock(GeminiLiveVoiceSession.class);
        when(f.gemini.createTextContinuationSession(eq(f.context), eq(f.transport), any(Consumer.class)))
                .thenReturn(fallback);
        DeepgramHybridVoiceSession session = f.session();

        session.start();
        session.onOpen(f.socket);
        session.onText(f.socket, results("Quiero reservar para mañana"), true);

        verify(f.facts, never()).answer(any(), anyString());
        verify(f.gemini).createTextContinuationSession(eq(f.context), eq(f.transport), any(Consumer.class));
        verify(fallback).start();
        verify(fallback).acceptInboundText("Quiero reservar para mañana");

        session.close();
        verify(fallback).close();
    }

    private static String results(String transcript) {
        return new org.json.JSONObject()
                .put("type", "Results")
                .put("is_final", true)
                .put("speech_final", true)
                .put("channel", new org.json.JSONObject()
                        .put("alternatives", new org.json.JSONArray()
                                .put(new org.json.JSONObject().put("transcript", transcript))))
                .toString();
    }

    private static final class Fixture {
        final RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911112222", "+56220000000", "stream");
        final DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        final GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        final RealtimeToolService tools = mock(RealtimeToolService.class);
        final EconomicVoiceFactsService facts = mock(EconomicVoiceFactsService.class);
        final CallTranscriptService transcripts = mock(CallTranscriptService.class);
        final CallSummaryService summaries = mock(CallSummaryService.class);
        final CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        final VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        final TenantDatabaseContext database = new TenantDatabaseContext();
        final HttpClient http = mock(HttpClient.class);
        final WebSocket.Builder builder = mock(WebSocket.Builder.class);
        final WebSocket socket = mock(WebSocket.class);
        final VoiceTransportSession transport = mock(VoiceTransportSession.class);
        final HttpResponse<byte[]> ttsResponse = mock(HttpResponse.class);

        Fixture() {
            properties.setEnabled(true);
            properties.setDataProcessingApproved(true);
            properties.setApiKey("unit-test-key");
            when(http.newWebSocketBuilder()).thenReturn(builder);
            when(builder.connectTimeout(any())).thenReturn(builder);
            when(builder.header(anyString(), anyString())).thenReturn(builder);
            when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class)))
                    .thenReturn(CompletableFuture.completedFuture(socket));
            when(socket.sendText(any(CharSequence.class), anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(socket));
            when(socket.sendBinary(any(), anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(socket));
            when(socket.request(anyLong())).thenReturn(socket);
            when(socket.sendClose(anyInt(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(socket));
            when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(CompletableFuture.completedFuture(ttsResponse));
            when(ttsResponse.statusCode()).thenReturn(200);
            when(ttsResponse.body()).thenReturn(new byte[320]);
            when(transport.isOpen()).thenReturn(true);
            when(tools.agentGreeting(eq(context), anyString()))
                    .thenReturn("Hola, gracias por llamar. ¿En qué te ayudo?");
            when(facts.businessName(context)).thenReturn(Optional.of("Centro Aurora"));
        }

        DeepgramHybridVoiceSession session() {
            return new DeepgramHybridVoiceSession(
                    context, transport, properties, gemini, tools, facts, transcripts,
                    summaries, lifecycle, health, database, http);
        }
    }
}
