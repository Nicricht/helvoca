package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepgramEconomyVoiceSessionCoverageTest {
    private static final String PHONE = "+56911112222";
    private static final String PCMU = Base64.getEncoder().encodeToString(new byte[160]);

    @Test
    void connectFailureReturnsCallToGeminiInsteadOfClosingCarrier() {
        var f = new Fixture().enable().fallbackReady();
        var builder = mock(WebSocket.Builder.class);
        when(f.http.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.header(eq("Authorization"), anyString())).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), eq(f.session)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertDoesNotThrow(f.session::start);
        verify(f.geminiSession).start();
        verify(f.transport, never()).closeOnUpstreamFailure();
        verify(f.http).newWebSocketBuilder();
    }

    @Test
    void incomingAudioIsBoundedAndInvalidFramesNeverReachProvider() {
        for (String payload : new String[]{"A".repeat(8193),
                Base64.getEncoder().encodeToString(new byte[4200]), "@@@@"}) {
            var f = new Fixture().fallbackReady();
            f.session.acceptInboundAudio(payload);
            verify(f.geminiSession).start();
            verifyNoInteractions(f.http);
        }
    }

    @Test
    void missingAudioNullBlankAndAfterCloseDoNotSpendOrStart() {
        var f = new Fixture();
        f.session.acceptInboundAudio(null);
        f.session.acceptInboundAudio(" ");
        f.session.close();
        f.session.acceptInboundAudio(PCMU);
        verifyNoInteractions(f.gemini, f.tools, f.http);
    }

    @Test
    void bufferedPacketsAreFlushedToNovaSocketAndGreetingIsSynthesized() {
        var f = new Fixture().tts(new byte[]{1, 2, 3, 4}, 200);
        var ws = f.socket();
        when(f.tools.agentGreeting(eq(f.context), anyString())).thenReturn("Buen día");
        f.session.acceptInboundAudio(PCMU);
        f.session.onOpen(ws);
        verify(ws).sendBinary(any(ByteBuffer.class), eq(true));
        verify(f.tools).agentGreeting(eq(f.context), anyString());
        verify(f.http).sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        verify(f.transport).sendAudio(eq(f.context.streamSid()),
                eq(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4})));
        verifyNoInteractions(f.gemini);
    }

    @Test
    void greetingNullBlankAndTooLongUseFixedSafeDefault() {
        for (String greeting : new String[]{null, " ", "a".repeat(161)}) {
            var f = new Fixture().tts(new byte[]{0, 1}, 200);
            when(f.tools.agentGreeting(eq(f.context), anyString())).thenReturn(greeting);
            f.session.onOpen(f.socket());
            verify(f.http).sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
            verifyNoInteractions(f.gemini);
        }
    }

    @Test
    void outboundWebsocketFailureHandsOffToGeminiOnce() throws Exception {
        var f = new Fixture().fallbackReady();
        var socket = mock(WebSocket.class);
        when(socket.sendBinary(any(ByteBuffer.class), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("lost")));
        field(f.session, "socket", socket);
        f.session.acceptInboundAudio(PCMU);
        verify(f.geminiSession).start();
        verify(socket).abort();
    }

    @Test
    void pendingAudioBackpressureTransfersToGeminiInsteadOfOverrunningMemory() {
        var f = new Fixture().fallbackReady();
        for (int i = 0; i < 200; i++) f.session.acceptInboundAudio(PCMU);
        verifyNoInteractions(f.gemini);
        f.session.acceptInboundAudio(PCMU);
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
    }

    @Test
    void outgoingAudioBackpressureTransfersToGemini() throws Exception {
        var f = new Fixture().fallbackReady();
        field(f.session, "socket", f.socket());
        field(f.session, "pendingSendCount", 200);
        f.session.acceptInboundAudio(PCMU);
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
    }

    @Test
    void partialTranscriptsOnlyDispatchAfterFinalChunk() {
        var f = new Fixture().fallbackReady();
        var ws = f.socket();
        String json = transcript("quiero reservar", true, true).toString();
        f.session.onText(ws, json.substring(0, 8), false);
        verifyNoInteractions(f.gemini);
        f.session.onText(ws, json.substring(8), true);
        verify(f.geminiSession).start();
        verify(ws, times(2)).request(1);
    }

    @Test
    void invalidAndOversizedStreamingMessagesAlwaysFallBackSafely() {
        for (String value : new String[]{"not-json", "a".repeat(32769)}) {
            var f = new Fixture().fallbackReady();
            f.session.onText(f.socket(), value, true);
            verify(f.geminiSession).start();
            verifyNoInteractions(f.http);
        }
    }

    @Test
    void ignoredAndIncompleteTranscriptsNeverMakePaidSpeech() {
        var f = new Fixture();
        f.session.consumeTranscript(new JSONObject().put("type", "Metadata"));
        f.session.consumeTranscript(new JSONObject().put("type", "Results").put("is_final", false));
        f.session.consumeTranscript(new JSONObject().put("type", "Results").put("is_final", true));
        f.session.consumeTranscript(new JSONObject().put("type", "Results")
                .put("is_final", true).put("channel", new JSONObject()));
        f.session.consumeTranscript(new JSONObject().put("type", "Results")
                .put("is_final", true).put("channel", new JSONObject()
                        .put("alternatives", new JSONArray().put(JSONObject.NULL))));
        f.session.consumeTranscript(transcript("", true, true));
        verifyNoInteractions(f.gemini, f.http, f.tools);
    }

    @Test
    void exceedinglyLongFinalTranscriptHandsOffToGeminiWithoutAnswer() {
        var f = new Fixture().fallbackReady();
        f.session.consumeTranscript(transcript("x".repeat(1025), true, true));
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http, f.tools);
    }

    @Test
    void overlappingRequestDuringPendingTtsCancelsEconomicSpeechPath() {
        var f = new Fixture().fallbackReady().catalog();
        CompletableFuture<HttpResponse<byte[]>> pending = f.pendingTts();
        f.session.consumeTranscript(transcript("¿Cuánto cuesta psicología?", true, true));
        verifyNoInteractions(f.gemini);
        f.session.consumeTranscript(transcript("¿Cuánto cuesta psicología?", true, true));
        verify(f.geminiSession).start();
        pending.complete(f.response(200, new byte[]{1, 2, 3}));
        verify(f.transport, never()).sendAudio(anyString(), anyString());
    }

    @Test
    void failedTenantPlaybackClearReturnsToGeminiWithoutTts() {
        var f = new Fixture().fallbackReady().catalog();
        doThrow(new IllegalStateException("audio queue unavailable"))
                .when(f.transport).clearPlayback(f.context.streamSid());
        f.session.consumeTranscript(transcript("¿Cuánto cuesta psicología?", true, true));
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
    }

    @Test
    void ttsCostGateRunsBeforePaidProviderRequest() {
        var f = new Fixture().fallbackReady();
        f.settings.setGeminiReferenceUsdPerMinute(0);
        f.session.onOpen(f.socket());
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
    }

    @Test
    void tooManyCharactersProhibitNewTtsEvenWithPositiveReferencePrice() throws Exception {
        var f = new Fixture().fallbackReady();
        field(f.session, "sentCharacters", new java.util.concurrent.atomic.AtomicInteger(220));
        f.session.onOpen(f.socket());
        verify(f.geminiSession).start();
        verifyNoInteractions(f.http);
    }

    @Test
    void ttsHttpFailureNullResponseBadStatusEmptyOrOversizedAudioFallsBack() {
        for (int mode = 0; mode < 5; mode++) {
            var f = new Fixture().fallbackReady();
            if (mode == 0) f.ttsFailure();
            else if (mode == 1) f.ttsNull();
            else if (mode == 2) f.tts(new byte[]{1}, 500);
            else if (mode == 3) f.tts(new byte[0], 200);
            else f.tts(new byte[512001], 200);
            f.session.onOpen(f.socket());
            verify(f.geminiSession).start();
            verify(f.transport, never()).sendAudio(anyString(), anyString());
        }
    }

    @Test
    void successfulTtsChunksPcmuAndStopsOnClosedTransport() {
        var f = new Fixture().tts(new byte[3201], 200);
        when(f.transport.isOpen()).thenReturn(true);
        f.session.onOpen(f.socket());
        verify(f.transport, times(3)).sendAudio(eq(f.context.streamSid()), anyString());
        var g = new Fixture().tts(new byte[3201], 200);
        when(g.transport.isOpen()).thenReturn(false);
        g.session.onOpen(g.socket());
        verify(g.transport, never()).sendAudio(anyString(), anyString());
    }

    @Test
    void lateTtsCompletionAfterHangupDoesNotSendAudio() {
        var f = new Fixture();
        CompletableFuture<HttpResponse<byte[]>> pending = f.pendingTts();
        f.session.onOpen(f.socket());
        f.session.close();
        pending.complete(f.response(200, new byte[]{1, 2}));
        verify(f.transport, never()).sendAudio(anyString(), anyString());
        verifyNoInteractions(f.gemini);
    }

    @Test
    void geminiStartupFailureFailsClosedAndLateWebsocketCloseIsIdempotent() {
        var f = new Fixture();
        when(f.gemini.configuredFor(PHONE)).thenReturn(true);
        when(f.gemini.createSession(eq(f.context), eq(f.transport)))
                .thenThrow(new IllegalStateException("offline backup"));
        f.session.onClose(f.socket(), 1006, "offline");
        verify(f.transport).closeOnUpstreamFailure();
        f.session.onClose(f.socket(), 1006, "again");
        verify(f.transport, times(1)).closeOnUpstreamFailure();
    }

    @Test
    void closingGeminiHandoffClosesBothOnce() throws Exception {
        var f = new Fixture().fallbackReady();
        var socket = f.socket();
        field(f.session, "socket", socket);
        f.session.onError(socket, new IllegalStateException("provider reset"));
        verify(f.geminiSession).start();
        f.session.close();
        f.session.close();
        verify(socket).abort();
        verify(f.geminiSession).close();
    }


    @Test
    void handoffForwardsSubsequentAudioOnlyToGeminiAndIgnoresUnpublishedHandoff() throws Exception {
        var pending = new Fixture();
        flag(pending.session, "handedToGemini");
        pending.session.acceptInboundAudio(PCMU);
        verifyNoInteractions(pending.http, pending.gemini, pending.tools);

        var active = new Fixture().fallbackReady();
        active.session.onError(active.socket(), new IllegalStateException("network lost"));
        active.session.acceptInboundAudio(PCMU);
        verify(active.geminiSession).start();
        verify(active.geminiSession).acceptInboundAudio(PCMU);
        verifyNoInteractions(active.http);
    }

    @Test
    void unsentFramesAfterSocketResetHandoffOrClosureNeverLeaveTheProcess() throws Exception {
        var missing = new Fixture();
        invoke(missing.session, "sendFrame", byte[].class, new byte[]{1, 2});
        verifyNoInteractions(missing.http);

        var handed = new Fixture();
        WebSocket handoffSocket = handed.socket();
        field(handed.session, "socket", handoffSocket);
        flag(handed.session, "handedToGemini");
        invoke(handed.session, "sendFrame", byte[].class, new byte[]{1, 2});
        verify(handoffSocket, never()).sendBinary(any(ByteBuffer.class), anyBoolean());

        var closed = new Fixture();
        closed.session.close();
        WebSocket closedSocket = closed.socket();
        field(closed.session, "socket", closedSocket);
        invoke(closed.session, "sendFrame", byte[].class, new byte[]{1, 2});
        verify(closedSocket, never()).sendBinary(any(ByteBuffer.class), anyBoolean());
    }

    @Test
    void websocketOpenArrivingAfterHandoffOrCloseIsAbortedWithoutGreetingOrAudio() {
        var handed = new Fixture().fallbackReady();
        handed.session.onError(handed.socket(), new IllegalStateException("disconnected"));
        WebSocket late = handed.socket();
        handed.session.onOpen(late);
        verify(late).abort();
        verifyNoInteractions(handed.tools, handed.http);

        var closed = new Fixture();
        closed.session.close();
        WebSocket afterClose = closed.socket();
        closed.session.onOpen(afterClose);
        verify(afterClose).abort();
        verifyNoInteractions(closed.tools, closed.http);
    }

    @Test
    void lateSpeechAndSynthesisCallbacksAfterCloseOrHandoffCannotCallPaidProviders() throws Exception {
        var closed = new Fixture();
        closed.session.close();
        invoke(closed.session, "onUtterance", String.class, "¿Cuánto cuesta psicología?");
        invoke(closed.session, "synthesize", String.class, "No responder después del cierre");
        verifyNoInteractions(closed.http, closed.tools, closed.gemini);

        var handed = new Fixture().fallbackReady();
        handed.session.onError(handed.socket(), new IllegalStateException("handoff"));
        invoke(handed.session, "onUtterance", String.class, "¿Cuánto cuesta psicología?");
        invoke(handed.session, "synthesize", String.class, "No hablar en paralelo");
        verify(handed.geminiSession).start();
        verifyNoInteractions(handed.http, handed.tools);
    }

    @Test
    void ttsHttp200WithNoAudioBodyFallsBackInsteadOfSilentlyClaimingSpeech() {
        var f = new Fixture().fallbackReady().tts(null, 200);
        f.session.onOpen(f.socket());
        verify(f.geminiSession).start();
        verify(f.transport, never()).sendAudio(anyString(), anyString());
    }

    @Test
    void audioChunkingStopsImmediatelyWhenCallClosesOrTransfersBetweenChunks() throws Exception {
        var closed = new Fixture().tts(new byte[3201], 200);
        doAnswer(invocation -> {
            closed.session.close();
            return null;
        }).when(closed.transport).sendAudio(eq(closed.context.streamSid()), anyString());
        closed.session.onOpen(closed.socket());
        verify(closed.transport, times(1)).sendAudio(eq(closed.context.streamSid()), anyString());

        var handed = new Fixture().tts(new byte[3201], 200);
        doAnswer(invocation -> {
            flag(handed.session, "handedToGemini");
            return null;
        }).when(handed.transport).sendAudio(eq(handed.context.streamSid()), anyString());
        handed.session.onOpen(handed.socket());
        verify(handed.transport, times(1)).sendAudio(eq(handed.context.streamSid()), anyString());
    }

    private static JSONObject transcript(String text, boolean isFinal, boolean speechFinal) {
        return new JSONObject().put("type", "Results").put("is_final", isFinal)
                .put("speech_final", speechFinal).put("channel",
                        new JSONObject().put("alternatives", new JSONArray()
                                .put(new JSONObject().put("transcript", text))));
    }

    private static void field(Object subject, String name, Object value) throws Exception {
        Field f = subject.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(subject, value);
    }


    private static void flag(Object subject, String name) throws Exception {
        Field f = subject.getClass().getDeclaredField(name);
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) f.get(subject)).set(true);
    }

    private static void invoke(Object subject, String method, Class<?> argType, Object arg)
            throws Exception {
        var target = subject.getClass().getDeclaredMethod(method, argType);
        target.setAccessible(true);
        target.invoke(subject, arg);
    }

    private static final class Fixture {
        final RealtimeCallContext context = new RealtimeCallContext(UUID.randomUUID(),
                UUID.randomUUID(), null, "+56922223333", PHONE, "MZ-synthetic");
        final DeepgramEconomyVoiceProperties settings = new DeepgramEconomyVoiceProperties();
        final VoiceTransportSession transport = mock(VoiceTransportSession.class);
        final RealtimeToolService tools = mock(RealtimeToolService.class);
        final GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        final VoiceAiSession geminiSession = mock(VoiceAiSession.class);
        final HttpClient http = mock(HttpClient.class);
        final DeepgramEconomyVoiceSession session = new DeepgramEconomyVoiceSession(
                context, transport, settings,
                new DeepgramEconomyPublicResponder(tools), tools, gemini, http);

        Fixture enable() {
            settings.setEnabled(true);
            settings.setApiKey("synthetic-only");
            settings.setPinnedBusinessId(context.businessId().toString());
            settings.setPinnedDestinationNumber(PHONE);
            return this;
        }

        Fixture fallbackReady() {
            when(gemini.configuredFor(PHONE)).thenReturn(true);
            when(gemini.createSession(eq(context), eq(transport))).thenReturn(geminiSession);
            return this;
        }

        Fixture catalog() {
            when(tools.execute(eq(context), eq("list_services"), eq("{}")))
                    .thenReturn(new JSONObject().put("success", true)
                            .put("data", new JSONObject().put("services", new JSONArray()
                                    .put(new JSONObject().put("name", "Psicología")
                                            .put("price", 35000)))).toString());
            return this;
        }

        WebSocket socket() {
            WebSocket ws = mock(WebSocket.class);
            when(ws.sendBinary(any(ByteBuffer.class), eq(true)))
                    .thenReturn(CompletableFuture.completedFuture(ws));
            return ws;
        }

        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response(int status, byte[] bytes) {
            HttpResponse<byte[]> r = mock(HttpResponse.class);
            when(r.statusCode()).thenReturn(status);
            when(r.body()).thenReturn(bytes);
            return r;
        }

        Fixture tts(byte[] audio, int status) {
            HttpResponse<byte[]> preparedResponse = response(status, audio);
            when(http.sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                    .thenReturn(CompletableFuture.completedFuture(preparedResponse));
            when(transport.isOpen()).thenReturn(true);
            return this;
        }

        CompletableFuture<HttpResponse<byte[]>> pendingTts() {
            CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
            when(http.sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                    .thenReturn(pending);
            when(transport.isOpen()).thenReturn(true);
            return pending;
        }

        Fixture ttsFailure() {
            when(http.sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("failure")));
            return this;
        }

        Fixture ttsNull() {
            when(http.sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            return this;
        }
    }
}
