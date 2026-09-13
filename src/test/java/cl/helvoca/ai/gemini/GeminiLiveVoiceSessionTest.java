package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveVoiceSessionTest {

    @Test
    void setupUsesNativeAudioVoiceTranscriptsAndRecepVozTools() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        JSONObject root = session.buildSetup();
        JSONObject setup = root.getJSONObject("setup");

        assertEquals("models/gemini-3.1-flash-live-preview", setup.getString("model"));
        JSONArray modalities = setup.getJSONObject("generationConfig").getJSONArray("responseModalities");
        assertEquals("AUDIO", modalities.getString(0));
        assertEquals("Kore", setup.getJSONObject("generationConfig")
                .getJSONObject("speechConfig")
                .getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig")
                .getString("voiceName"));
        assertTrue(setup.has("inputAudioTranscription"));
        assertTrue(setup.has("outputAudioTranscription"));

        String instructions = setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(instructions.contains("Reglas oficiales del negocio"));
        assertTrue(instructions.contains("RecepVoz"));
        assertTrue(instructions.contains("[RECEPVOZ_CALL_CONNECTED]"));

        JSONArray declarations = setup.getJSONArray("tools")
                .getJSONObject(0).getJSONArray("functionDeclarations");
        assertTrue(declarations.length() >= 10);
        assertTrue(hasFunction(declarations, "create_booking"));
        assertTrue(hasFunction(declarations, "list_available_slots"));
        assertTrue(hasFunction(declarations, "transfer_to_human"));
        assertFalse(declarations.toString().contains("\"additionalProperties\""),
                "Gemini Live rejects additionalProperties in FunctionDeclaration parameters");
    }

    @Test
    void binarySetupCompleteUnlocksProviderPersistsMilestoneAndStartsOpeningTurn() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);

        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        health.failure(GeminiLiveVoiceProvider.ID,
                VoiceProviderHealthRegistry.FailureKind.UPSTREAM,
                "test precondition");

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        when(socket.sendClose(anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                lifecycle,
                mock(CallCertificationService.class),
                health,
                HttpClient.newHttpClient());

        session.onOpen(socket);

        byte[] payload = "{\"setupComplete\":{}}".getBytes(StandardCharsets.UTF_8);
        int split = payload.length / 2;
        session.onBinary(socket, ByteBuffer.wrap(Arrays.copyOfRange(payload, 0, split)), false);
        assertEquals("OPEN", health.snapshot(GeminiLiveVoiceProvider.ID, true).state());

        session.onBinary(socket, ByteBuffer.wrap(Arrays.copyOfRange(payload, split, payload.length)), true);
        assertEquals("READY", health.snapshot(GeminiLiveVoiceProvider.ID, true).state());
        verify(lifecycle).markAiSetupCompleted(context.callId());

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, atLeast(2)).sendText(sent.capture(), eq(true));
        String opening = sent.getAllValues().stream()
                .map(CharSequence::toString)
                .filter(message -> message.contains("[RECEPVOZ_CALL_CONNECTED]"))
                .findFirst()
                .orElseThrow();
        JSONObject openingJson = new JSONObject(opening);
        assertFalse(openingJson.has("clientContent"));
        assertEquals("[RECEPVOZ_CALL_CONNECTED]",
                openingJson.getJSONObject("realtimeInput").getString("text"));
    }

    @Test
    void certificationScenarioWaitsForAvailabilityCreationAndCancellationMilestones() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(true);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        UUID bookingId = UUID.randomUUID();
        when(tools.execute(eq(context), anyString(), anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(1);
            JSONObject data = new JSONObject();
            if ("list_available_slots".equals(name)) {
                data.put("slots", new JSONArray().put(new JSONObject()
                        .put("startAt", "2026-09-14T22:00:00Z")));
            }
            if ("create_booking".equals(name) || "cancel_booking".equals(name)) {
                data.put("bookingId", bookingId.toString());
            }
            return new JSONObject()
                    .put("success", true)
                    .put("data", data)
                    .put("error", JSONObject.NULL)
                    .toString();
        });

        CallTranscriptService transcripts = mock(CallTranscriptService.class);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        when(socket.sendClose(anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                transcripts,
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        session.onOpen(socket);
        session.onText(socket, "{\"setupComplete\":{}}", true);

        session.onText(socket, turnComplete(), true);
        verify(transcripts, times(1)).append(eq(context.callId()), eq("USER"),
                contains("Confirmo explícitamente"));

        session.onText(socket, turnComplete(), true);
        verify(transcripts, times(1)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, toolCall("slot-1", "list_available_slots"), true);
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("Ejecuta ahora create_booking"));
        verify(transcripts, times(2)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, toolCall("book-1", "create_booking"), true);
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("ejecuta cancel_booking ahora"));
        verify(transcripts, times(3)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, toolCall("cancel-1", "cancel_booking"), true);
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("La cancelación ya devolvió success=true"));
        verify(transcripts, times(4)).append(eq(context.callId()), eq("USER"), anyString());

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, atLeast(5)).sendText(sent.capture(), eq(true));
        assertTrue(sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .anyMatch(message -> message.optJSONObject("realtimeInput") != null
                        && message.getJSONObject("realtimeInput").optString("text", "")
                        .contains("Ejecuta ahora create_booking")));
        assertTrue(sent.getAllValues().stream()
                .map(CharSequence::toString)
                .noneMatch(message -> message.contains("\"clientContent\"")));
    }

    @Test
    void certificationSimulationDoesNotForwardCarrierMicrophoneAudio() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(true);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        when(socket.sendClose(anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                mock(VoiceTransportSession.class),
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        session.onOpen(socket);
        session.onText(socket, "{\"setupComplete\":{}}", true);
        clearInvocations(socket);

        session.acceptInboundAudio(Base64.getEncoder().encodeToString(new byte[160]));

        verify(socket, never()).sendText(any(CharSequence.class), anyBoolean());
    }

    @Test
    void permissionDeniedAndAccessDeniedAreAuthFailures() {
        assertEquals(VoiceProviderHealthRegistry.FailureKind.AUTH,
                GeminiLiveVoiceSession.classifyFailure(403, "PERMISSION_DENIED"));
        assertEquals(VoiceProviderHealthRegistry.FailureKind.AUTH,
                GeminiLiveVoiceSession.classifyFailure(1008, "Your project has been denied access"));
        assertEquals(VoiceProviderHealthRegistry.FailureKind.AUTH,
                GeminiLiveVoiceSession.classifyFailure(null, "permission denied for API key"));
    }

    @Test
    void policyCloseWithoutAuthSignalRemainsUpstream() {
        assertEquals(VoiceProviderHealthRegistry.FailureKind.UPSTREAM,
                GeminiLiveVoiceSession.classifyFailure(1008, "invalid setup payload"));
    }

    private static GeminiLiveProperties properties() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("gemini-test-key");
        properties.setModel("gemini-3.1-flash-live-preview");
        properties.setVoice("Kore");
        return properties;
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-test");
    }

    private static String turnComplete() {
        return new JSONObject()
                .put("serverContent", new JSONObject().put("turnComplete", true))
                .toString();
    }

    private static String toolCall(String id, String name) {
        return new JSONObject()
                .put("toolCall", new JSONObject()
                        .put("functionCalls", new JSONArray().put(new JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("args", new JSONObject()))))
                .toString();
    }

    private static boolean hasFunction(JSONArray declarations, String name) {
        for (int i = 0; i < declarations.length(); i++) {
            if (name.equals(declarations.getJSONObject(i).optString("name"))) return true;
        }
        return false;
    }
}