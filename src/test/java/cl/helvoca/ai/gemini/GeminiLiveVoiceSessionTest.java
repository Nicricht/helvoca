package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
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
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());

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
        JSONObject inputTranscription = setup.getJSONObject("inputAudioTranscription");
        assertEquals("es-CL", inputTranscription.getJSONArray("languageCodes").getString(0));
        assertEquals("VERBATIM", inputTranscription.getString("mode"));
        assertTrue(inputTranscription.getJSONArray("customVocabulary").toList().contains("RecepVoz"));
        assertFalse(setup.has("proactivity"));

        String instructions = setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(instructions.contains("Reglas oficiales del negocio"));
        assertTrue(instructions.contains("RecepVoz"));
        assertTrue(instructions.contains("[RECEPVOZ_CALL_CONNECTED]"));
        assertTrue(instructions.contains("ESPAÑOL DE CHILE"));
        assertTrue(instructions.contains("CERO CALL CENTER"));
        assertTrue(instructions.contains("NO vuelvas a ejecutar la misma herramienta"));
        assertTrue(instructions.contains("cadencia urbana de Santiago de Chile"));
        assertTrue(instructions.contains("voz claramente femenina, joven-adulta y luminosa"));
        assertTrue(instructions.contains("mujer joven de Santiago"));
        assertTrue(instructions.contains("sonrisa audible"));
        assertTrue(instructions.contains("DESDE LA PRIMERA SÍLABA"));
        assertTrue(instructions.contains("Ya, cuéntame, ¿en qué te ayudo?"));
        assertTrue(instructions.contains("me pueda colaborar"));
        assertTrue(instructions.contains("habla rápido-natural"));
        assertTrue(instructions.contains("LATENCIA VOCAL"));
        assertTrue(instructions.contains("No uses suspiros"));
        assertTrue(instructions.contains("qué agradable hablar con ella"));
        assertTrue(instructions.contains("está prohibido sonar como operadora"));
        assertTrue(instructions.contains("qué rico saludarte"));
        assertTrue(instructions.contains("ALEGRÍA COMERCIAL"));
        assertTrue(instructions.contains("está prohibido hacer dos veces la misma pregunta"));
        assertTrue(instructions.contains("mantén exactamente el mismo género"));
        assertTrue(instructions.contains("find_caller antes de pedir nombre o teléfono"));
        assertTrue(instructions.contains("NO vuelvas a preguntar nada"));
        assertTrue(instructions.contains("completar una reserva, venta o consulta NO significa que la llamada terminó"));
        assertTrue(instructions.contains("cadencia urbana de Santiago de Chile"));
        assertTrue(instructions.contains("a las nueve y media"));
        assertTrue(instructions.contains("nunca menciones UUID"));
        assertTrue(instructions.contains("success=true sin bookingId es solo una propuesta pendiente de confirmación"));
        assertTrue(instructions.contains("Solo puedes afirmar que la reserva existe"));

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
    void finalistBakeOffAdvancesFiveConversationTurnsEndsAfterPlaybackAndIgnoresPhoneAudio() {
        GeminiLiveProperties properties = properties();
        properties.setVoice("Sadachbia");
        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56966939611", "+14355652512", "MZ-bakeoff", "Sadachbia");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());
        when(tools.prepareDeferredEndCall(context)).thenReturn(new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("ended", false)
                        .put("pendingPlaybackCompletion", true))
                .put("error", JSONObject.NULL)
                .toString());

        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(transport.endAfterPlayback()).thenReturn(true);
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                lifecycle,
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        JSONObject setup = session.buildSetup().getJSONObject("setup");
        assertEquals("Sadachbia", setup.getJSONObject("generationConfig")
                .getJSONObject("speechConfig")
                .getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig")
                .getString("voiceName"));

        String instructions = setup.getJSONObject("systemInstruction")
                .getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(instructions.contains("MODO VOICE FINALIST DE RECEPVOZ"));
        assertTrue(instructions.contains("[RECEPVOZ_VOICE_BAKEOFF_LINE_1]"));
        assertTrue(instructions.contains("[RECEPVOZ_VOICE_BAKEOFF_LINE_2]"));
        assertTrue(instructions.contains("[RECEPVOZ_VOICE_BAKEOFF_LINE_3]"));
        assertTrue(instructions.contains("[RECEPVOZ_VOICE_BAKEOFF_LINE_4]"));
        assertTrue(instructions.contains("[RECEPVOZ_VOICE_BAKEOFF_LINE_5]"));
        assertTrue(instructions.contains("Hola, gracias por llamar. Ya, cuéntame, ¿en qué te ayudo?"));
        assertTrue(instructions.contains("Entonces buscas una hora para mañana"));
        assertTrue(instructions.contains("Tengo una a las diez y media y otra a las doce"));
        assertTrue(instructions.contains("MISMA identidad vocal"));
        assertTrue(instructions.contains("el sistema simula al cliente"));

        JSONArray declarations = setup.getJSONArray("tools")
                .getJSONObject(0).getJSONArray("functionDeclarations");
        assertEquals(1, declarations.length());
        assertTrue(hasFunction(declarations, "end_call"));
        assertFalse(hasFunction(declarations, "create_booking"));

        // Must return before decoding/sending even deliberately malformed phone audio.
        assertDoesNotThrow(() -> session.acceptInboundAudio("not-valid-base64"));
        verify(tools, never()).buildInstructions(context);

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        session.onOpen(socket);
        session.onText(socket, new JSONObject().put("setupComplete", new JSONObject()).toString(), true);

        String[] lines = {
                "Hola, gracias por llamar. Ya, cuéntame, ¿en qué te ayudo?",
                "Ya, perfecto. Entonces buscas una hora para mañana, ¿cierto?",
                "Sí, obvio. Tengo una a las diez y media y otra a las doce. ¿Cuál te acomoda más?",
                "Dale, las diez y media. Súper.",
                "Gracias por llamar, que estés súper. Chao."
        };
        for (String line : lines) {
            JSONObject completedTurn = new JSONObject().put("serverContent", new JSONObject()
                    .put("outputTranscription", new JSONObject().put("text", line))
                    .put("turnComplete", true));
            session.onText(socket, completedTurn.toString(), true);
        }

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, atLeast(6)).sendText(sent.capture(), eq(true));
        var markers = sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .filter(payload -> payload.has("clientContent"))
                .map(payload -> payload.getJSONObject("clientContent")
                        .getJSONArray("turns").getJSONObject(0)
                        .getJSONArray("parts").getJSONObject(0)
                        .getString("text"))
                .toList();

        assertEquals(Arrays.asList(
                "[RECEPVOZ_VOICE_BAKEOFF_LINE_1]",
                "[RECEPVOZ_VOICE_BAKEOFF_LINE_2]",
                "[RECEPVOZ_VOICE_BAKEOFF_LINE_3]",
                "[RECEPVOZ_VOICE_BAKEOFF_LINE_4]",
                "[RECEPVOZ_VOICE_BAKEOFF_LINE_5]"), markers);
        verify(tools, times(1)).prepareDeferredEndCall(context);
        verify(transport, times(1)).endAfterPlayback();
        verify(tools, never()).execute(eq(context), eq("end_call"), anyString());
        verify(lifecycle).markAiSetupCompleted(context.callId());
    }

    @Test
    void bakeOffEndCallFailureIsFailClosedAndDuplicateBoundaryDoesNotRetry() {
        GeminiLiveProperties properties = properties();
        properties.setVoice("Sadachbia");
        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56966939611", "+14355652512", "MZ-bakeoff-failure", "Sadachbia");
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());
        when(tools.prepareDeferredEndCall(context)).thenReturn(new JSONObject()
                .put("success", false)
                .put("error", new JSONObject().put("code", "END_CALL_TEST_FAILURE"))
                .toString());

        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        session.onOpen(socket);
        session.onText(socket, new JSONObject().put("setupComplete", new JSONObject()).toString(), true);

        JSONObject turnComplete = new JSONObject()
                .put("serverContent", new JSONObject().put("turnComplete", true));
        for (int i = 0; i < 5; i++) {
            session.onText(socket, turnComplete.toString(), true);
        }

        // A duplicate boundary after the failed one-shot end_call must not retry it.
        session.onText(socket, turnComplete.toString(), true);

        verify(tools, times(1)).prepareDeferredEndCall(context);
        verify(transport, never()).endAfterPlayback();
    }

    @Test
    void maleProfileDoesNotReceiveFemaleVoiceInstructions() {
        GeminiLiveProperties properties = properties();
        properties.setVoice("Enceladus");
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());

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

        String instructions = session.buildSetup()
                .getJSONObject("setup")
                .getJSONObject("systemInstruction")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");

        assertTrue(instructions.contains("voz claramente masculina y adulta"));
        assertTrue(instructions.contains("mismo género"));
        assertFalse(instructions.contains("primera frase debe usar de inmediato una voz claramente femenina"));
    }

    @Test
    void removesUnsupportedFieldsFromNestedGeminiToolSchemas() {
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("$schema", "https://json-schema.org/draft/2020-12/schema")
                .put("properties", new JSONObject()
                        .put("metadata", new JSONObject()
                                .put("type", "object")
                                .put("additionalProperties", true)));

        JSONObject sanitized = GeminiLiveVoiceSession.sanitizeGeminiSchema(schema);

        assertFalse(sanitized.toString().contains("\"additionalProperties\""));
        assertFalse(sanitized.toString().contains("\"$schema\""));
        assertTrue(sanitized.getJSONObject("properties").has("metadata"));
    }

    @Test
    void setupPublishesOnlyTenantAllowedTools() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(
                new JSONArray().put(RealtimeToolDefinitions.endCall()));

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

        JSONArray declarations = session.buildSetup()
                .getJSONObject("setup")
                .getJSONArray("tools")
                .getJSONObject(0)
                .getJSONArray("functionDeclarations");

        assertEquals(1, declarations.length());
        assertTrue(hasFunction(declarations, "end_call"));
        assertFalse(hasFunction(declarations, "create_booking"));
        verify(tools).toolDefinitions(context);
    }

    @Test
    void localBargeInClearsCarrierPlaybackAfterTwoSpeechFrames() {
        GeminiLiveProperties properties = properties();
        properties.setLocalBargeInEnabled(true);
        properties.setLocalBargeInMeanAmplitudeThreshold(900);
        properties.setLocalBargeInSpeechFrames(2);
        properties.setLocalBargeInReleaseFrames(5);

        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.toolDefinitions(context)).thenReturn(RealtimeToolDefinitions.all());
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        CallLifecycleService lifecycle = mock(CallLifecycleService.class);

        WebSocket providerSocket = mock(WebSocket.class);
        when(providerSocket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(providerSocket));

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                lifecycle,
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        session.onOpen(providerSocket);
        session.onText(providerSocket, new JSONObject().put("setupComplete", new JSONObject()).toString(), true);

        ByteBuffer pcm24 = ByteBuffer.allocate(480 * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 480; i++) pcm24.putShort((short) 0);
        String modelAudio = Base64.getEncoder().encodeToString(pcm24.array());
        JSONObject assistantAudio = new JSONObject().put("serverContent", new JSONObject()
                .put("modelTurn", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("inlineData", new JSONObject()
                                        .put("data", modelAudio)
                                        .put("mimeType", "audio/pcm;rate=24000"))))));
        session.onText(providerSocket, assistantAudio.toString(), true);

        byte[] loud = new byte[160];
        Arrays.fill(loud, PcmuAudioCodec.encodeMulaw((short) 8_000));
        String loudBase64 = Base64.getEncoder().encodeToString(loud);

        session.acceptInboundAudio(loudBase64);
        verify(transport, never()).clearPlayback(context.streamSid());

        session.acceptInboundAudio(loudBase64);
        verify(transport, times(1)).clearPlayback(context.streamSid());

        byte[] silence = new byte[160];
        Arrays.fill(silence, (byte) 0xff);
        String silenceBase64 = Base64.getEncoder().encodeToString(silence);
        for (int i = 0; i < 5; i++) {
            session.acceptInboundAudio(silenceBase64);
        }

        session.onText(providerSocket, assistantAudio.toString(), true);
        verify(transport, times(2)).sendAudio(eq(context.streamSid()), anyString());
    }

    @Test
    void explicitClosingIntentRecognizerRejectsSimpleConfirmation() {
        assertFalse(GeminiLiveVoiceSession.hasExplicitClosingIntent("Sí."));
        assertFalse(GeminiLiveVoiceSession.hasExplicitClosingIntent("Perfecto."));
        assertTrue(GeminiLiveVoiceSession.hasExplicitClosingIntent("No, gracias."));
        assertTrue(GeminiLiveVoiceSession.hasExplicitClosingIntent("Eso es todo, chao."));
        assertTrue(GeminiLiveVoiceSession.hasExplicitClosingIntent("Puedes cortar la llamada."));
        assertFalse(GeminiLiveVoiceSession.hasClosingIntent("No.", "¿Quieres otro horario?", ""));
        assertTrue(GeminiLiveVoiceSession.hasClosingIntent(
                "No.", "¿Necesitas algo más?", ""));
        assertTrue(GeminiLiveVoiceSession.hasClosingIntent(
                "No.", "Muchas gracias por llamar. Que estés muy bien.", "¿Necesitas algo más?"));
    }

    @Test
    void endCallIsBlockedAfterSimpleConfirmationWithoutGoodbye() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        VoiceTransportSession transport = mock(VoiceTransportSession.class);

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        WebSocket providerSocket = mock(WebSocket.class);
        JSONObject userConfirmation = new JSONObject()
                .put("serverContent", new JSONObject()
                        .put("inputTranscription", new JSONObject().put("text", "Sí."))
                        .put("turnComplete", true));
        session.onText(providerSocket, userConfirmation.toString(), true);

        JSONObject toolCall = new JSONObject().put("toolCall", new JSONObject()
                .put("functionCalls", new JSONArray().put(new JSONObject()
                        .put("id", "end-premature")
                        .put("name", "end_call")
                        .put("args", new JSONObject()))));
        session.onText(providerSocket, toolCall.toString(), true);

        verify(tools, never()).prepareDeferredEndCall(context);
        verify(transport, never()).endAfterPlayback();
    }

    @Test
    void endCallAcceptsNoAfterAnythingElseQuestion() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(tools.prepareDeferredEndCall(context)).thenReturn(new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("ended", false)
                        .put("pendingPlaybackCompletion", true))
                .put("error", JSONObject.NULL)
                .toString());

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        WebSocket providerSocket = mock(WebSocket.class);
        session.onText(providerSocket, generationCompleteWithOutput("¿Necesitas algo más?"), true);
        session.onText(providerSocket, new JSONObject()
                .put("serverContent", new JSONObject()
                        .put("inputTranscription", new JSONObject().put("text", "No."))
                        .put("turnComplete", true))
                .toString(), true);
        session.onText(providerSocket, generationCompleteWithOutput(
                "Muchas gracias por llamar. Que estés muy bien. Hasta luego."), true);

        session.onText(providerSocket, new JSONObject().put("toolCall", new JSONObject()
                .put("functionCalls", new JSONArray().put(new JSONObject()
                        .put("id", "end-after-no")
                        .put("name", "end_call")
                        .put("args", new JSONObject()))))
                .toString(), true);

        verify(tools).prepareDeferredEndCall(context);
    }

    @Test
    void endCallWaitsForGenerationBoundaryBeforeCarrierPlaybackHangup() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(tools.prepareDeferredEndCall(context)).thenReturn(new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("ended", false)
                        .put("pendingPlaybackCompletion", true))
                .put("error", JSONObject.NULL)
                .toString());
        when(transport.endAfterPlayback()).thenReturn(true);

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        WebSocket providerSocket = mock(WebSocket.class);
        JSONObject closingTurn = new JSONObject()
                .put("serverContent", new JSONObject()
                        .put("inputTranscription", new JSONObject().put("text", "No, gracias. Eso es todo."))
                        .put("turnComplete", true));
        session.onText(providerSocket, closingTurn.toString(), true);

        JSONObject toolCall = new JSONObject().put("toolCall", new JSONObject()
                .put("functionCalls", new JSONArray().put(new JSONObject()
                        .put("id", "end-1")
                        .put("name", "end_call")
                        .put("args", new JSONObject()))));
        session.onText(providerSocket, toolCall.toString(), true);

        verify(tools).prepareDeferredEndCall(context);
        verify(transport, never()).endAfterPlayback();

        JSONObject generationDone = new JSONObject()
                .put("serverContent", new JSONObject().put("generationComplete", true));
        session.onText(providerSocket, generationDone.toString(), true);

        verify(transport).endAfterPlayback();
        verify(tools, never()).execute(eq(context), eq("end_call"), anyString());
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
        JSONObject opening = sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .filter(message -> message.optJSONObject("realtimeInput") != null)
                .filter(message -> message.getJSONObject("realtimeInput").optString("text", "")
                        .contains("[RECEPVOZ_CALL_CONNECTED]"))
                .findFirst()
                .orElseThrow();
        assertFalse(opening.has("clientContent"));
        assertEquals("[RECEPVOZ_CALL_CONNECTED]",
                opening.getJSONObject("realtimeInput").getString("text"));
    }

    @Test
    void realCallCloseSchedulesCertificationReviewAndServiceFiltersNormalCalls() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(false);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        CallCertificationService certifications = mock(CallCertificationService.class);

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
                certifications,
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        session.onOpen(socket);
        session.close();

        verify(certifications).verifyAfterCall(context.callId());
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
        verify(transcripts, never()).append(eq(context.callId()), eq("USER"), contains("Ejecuta ahora create_booking"));
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("Ejecuta ahora create_booking"));
        verify(transcripts, times(2)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, toolCall("book-1", "create_booking"), true);
        verify(transcripts, never()).append(eq(context.callId()), eq("USER"), contains("ejecuta cancel_booking ahora"));
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("ejecuta cancel_booking ahora"));
        verify(transcripts, times(3)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, toolCall("cancel-1", "cancel_booking"), true);
        verify(transcripts, never()).append(eq(context.callId()), eq("USER"), contains("La cancelación ya devolvió success=true"));
        session.onText(socket, turnComplete(), true);
        verify(transcripts).append(eq(context.callId()), eq("USER"), contains("La cancelación ya devolvió success=true"));
        verify(transcripts, times(4)).append(eq(context.callId()), eq("USER"), anyString());

        ArgumentCaptor<CharSequence> sent = ArgumentCaptor.forClass(CharSequence.class);
        verify(socket, atLeast(5)).sendText(sent.capture(), eq(true));
        assertTrue(sent.getAllValues().stream()
                .map(CharSequence::toString)
                .map(JSONObject::new)
                .anyMatch(message -> {
                    JSONObject client = message.optJSONObject("clientContent");
                    if (client == null || !client.optBoolean("turnComplete", false)) return false;
                    JSONArray turns = client.optJSONArray("turns");
                    if (turns == null || turns.isEmpty()) return false;
                    JSONArray parts = turns.getJSONObject(0).optJSONArray("parts");
                    return parts != null
                            && !parts.isEmpty()
                            && parts.getJSONObject(0).optString("text", "")
                            .contains("Ejecuta ahora create_booking");
                }));
    }

    @Test
    void certificationScenarioAdvancesOnGenerationCompleteWithoutWaitingForPlaybackTurnComplete() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(true);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.execute(eq(context), eq("list_available_slots"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("slots", new JSONArray().put(
                                new JSONObject().put("startAt", "2026-09-14T22:00:00Z"))))
                        .put("error", JSONObject.NULL)
                        .toString());

        CallTranscriptService transcripts = mock(CallTranscriptService.class);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
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
        session.onText(socket, toolCall("slot-generation-complete", "list_available_slots"), true);

        verify(transcripts, never()).append(eq(context.callId()), eq("USER"),
                contains("Ejecuta ahora create_booking"));

        session.onText(socket, generationComplete(), true);

        verify(transcripts, never()).append(eq(context.callId()), eq("USER"),
                contains("Ejecuta ahora create_booking"));

        session.onText(socket, generationCompleteWithOutput("Ya revisé los horarios disponibles."), true);

        verify(transcripts).append(eq(context.callId()), eq("USER"),
                contains("Ejecuta ahora create_booking"));
        verify(transcripts, times(2)).append(eq(context.callId()), eq("USER"), anyString());

        session.onText(socket, turnComplete(), true);

        verify(transcripts, times(2)).append(eq(context.callId()), eq("USER"), anyString());
    }

    @Test
    void certificationScenarioConfirmsTwoPhaseBookingProposalBeforeTreatingItAsCreated() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(true);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        UUID operationId = UUID.randomUUID();
        when(tools.execute(eq(context), eq("list_available_slots"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("slots", new JSONArray()))
                        .put("error", JSONObject.NULL)
                        .toString());
        when(tools.execute(eq(context), eq("create_booking"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject()
                                .put("operationId", operationId.toString())
                                .put("confirmationToken", "confirm-test-token")
                                .put("requiresConfirmation", true))
                        .put("error", JSONObject.NULL)
                        .toString());

        CallTranscriptService transcripts = mock(CallTranscriptService.class);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
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
        session.onText(socket, toolCall("slot-proposal", "list_available_slots"), true);
        session.onText(socket, turnComplete(), true);
        session.onText(socket, toolCall("booking-proposal", "create_booking"), true);
        session.onText(socket, turnComplete(), true);

        verify(transcripts).append(eq(context.callId()), eq("USER"), argThat(text ->
                text.contains("solo una propuesta")
                        && text.contains("operationId")
                        && text.contains("confirmationToken")
                        && text.contains("Confirmo explícitamente")));
        verify(transcripts, never()).append(eq(context.callId()), eq("USER"),
                contains("ejecuta cancel_booking ahora"));
    }

    @Test
    void certificationScenarioAdvancesOnlyAfterTwoPhaseBookingReturnsBookingId() {
        GeminiLiveProperties properties = properties();
        properties.setCertificationSimulation(true);
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");

        UUID operationId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(tools.execute(eq(context), eq("list_available_slots"), anyString())).thenReturn(
                new JSONObject().put("success", true)
                        .put("data", new JSONObject().put("slots", new JSONArray()))
                        .put("error", JSONObject.NULL)
                        .toString());
        when(tools.execute(eq(context), eq("create_booking"), anyString()))
                .thenReturn(
                        new JSONObject().put("success", true)
                                .put("data", new JSONObject()
                                        .put("operationId", operationId.toString())
                                        .put("confirmationToken", "confirm-test-token")
                                        .put("requiresConfirmation", true))
                                .put("error", JSONObject.NULL)
                                .toString(),
                        new JSONObject().put("success", true)
                                .put("data", new JSONObject()
                                        .put("operationId", operationId.toString())
                                        .put("bookingId", bookingId.toString())
                                        .put("requiresConfirmation", false))
                                .put("error", JSONObject.NULL)
                                .toString());

        CallTranscriptService transcripts = mock(CallTranscriptService.class);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
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
        session.onText(socket, toolCall("slot-two-phase", "list_available_slots"), true);
        session.onText(socket, turnComplete(), true);

        session.onText(socket, toolCall("booking-proposal-two-phase", "create_booking"), true);
        session.onText(socket, turnComplete(), true);

        verify(transcripts).append(eq(context.callId()), eq("USER"), argThat(text ->
                text.contains("solo una propuesta")
                        && text.contains("operationId")
                        && text.contains("confirmationToken")
                        && text.contains("Confirmo explícitamente")));
        verify(transcripts, never()).append(eq(context.callId()), eq("USER"),
                contains("ejecuta cancel_booking ahora"));

        session.onText(socket, toolCall(
                "booking-confirm-two-phase",
                "create_booking",
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", "confirm-test-token")), true);
        session.onText(socket, turnComplete(), true);

        verify(tools, times(2)).execute(eq(context), eq("create_booking"), anyString());
        verify(transcripts).append(eq(context.callId()), eq("USER"),
                contains("ejecuta cancel_booking ahora"));
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
    void defaultLiveVoiceUsesYouthfulFemaleProfile() {
        assertEquals("Sulafat", new GeminiLiveProperties().getVoice());
    }

    @Test
    void repeatedIdenticalReadToolCallIsDeduplicatedWithinShortWindow() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.execute(eq(context), eq("list_available_slots"), anyString())).thenReturn(
                new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("slots", new JSONArray()))
                        .put("error", JSONObject.NULL)
                        .toString());

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
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

        String serviceId = UUID.randomUUID().toString();
        JSONObject firstArgs = new JSONObject()
                .put("serviceId", serviceId)
                .put("date", "2026-09-27");
        JSONObject sameSemanticArgsDifferentOrder = new JSONObject()
                .put("date", "2026-09-27")
                .put("serviceId", serviceId);

        session.onOpen(socket);
        session.onText(socket, toolCall("slot-duplicate-1", "list_available_slots", firstArgs), true);
        session.onText(socket, toolCall("slot-duplicate-2", "list_available_slots", sameSemanticArgsDifferentOrder), true);

        verify(tools, times(1)).execute(eq(context), eq("list_available_slots"), anyString());
    }

    @Test
    void sideEffectingToolCallsAreNeverDeduplicated() {
        GeminiLiveProperties properties = properties();
        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.execute(eq(context), eq("create_booking"), anyString())).thenReturn(
                new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("requiresConfirmation", true))
                        .put("error", JSONObject.NULL)
                        .toString());

        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
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

        JSONObject args = new JSONObject().put("serviceId", UUID.randomUUID().toString());

        session.onOpen(socket);
        session.onText(socket, toolCall("booking-1", "create_booking", args), true);
        session.onText(socket, toolCall("booking-2", "create_booking", args), true);

        verify(tools, times(2)).execute(eq(context), eq("create_booking"), anyString());
    }

    @Test
    void latencyBudgetBranchesAreExercisedForSlowToolBatchAndFirstAudio() throws Exception {
        GeminiLiveProperties properties = properties();
        properties.setToolLatencyBudgetMs(100);
        properties.setToolBatchLatencyBudgetMs(100);
        properties.setResponseLatencyBudgetMs(250);
        properties.setLocalBargeInEnabled(true);
        properties.setLocalBargeInMeanAmplitudeThreshold(900);
        properties.setLocalBargeInSpeechFrames(2);
        properties.setLocalBargeInReleaseFrames(5);

        RealtimeCallContext context = context();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        when(tools.buildInstructions(context)).thenReturn("Reglas oficiales del negocio");
        when(tools.execute(eq(context), eq("list_services"), anyString())).thenAnswer(invocation -> {
            Thread.sleep(130);
            return new JSONObject()
                    .put("success", true)
                    .put("data", new JSONObject().put("services", new JSONArray()))
                    .put("error", JSONObject.NULL)
                    .toString();
        });

        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(socket));

        GeminiLiveVoiceSession session = new GeminiLiveVoiceSession(
                context,
                transport,
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry(),
                HttpClient.newHttpClient());

        session.onOpen(socket);
        session.onText(socket, new JSONObject().put("setupComplete", new JSONObject()).toString(), true);
        session.onText(socket, toolCall("slow-list-services", "list_services"), true);
        verify(tools).execute(eq(context), eq("list_services"), anyString());

        byte[] loud = new byte[160];
        Arrays.fill(loud, PcmuAudioCodec.encodeMulaw((short) 8_000));
        String loudBase64 = Base64.getEncoder().encodeToString(loud);
        byte[] silence = new byte[160];
        Arrays.fill(silence, (byte) 0xff);
        String silenceBase64 = Base64.getEncoder().encodeToString(silence);

        session.acceptInboundAudio(loudBase64);
        session.acceptInboundAudio(loudBase64);
        for (int i = 0; i < 5; i++) session.acceptInboundAudio(silenceBase64);

        Thread.sleep(270);

        ByteBuffer pcm24 = ByteBuffer.allocate(480 * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 480; i++) pcm24.putShort((short) 0);
        String modelAudio = Base64.getEncoder().encodeToString(pcm24.array());
        JSONObject assistantAudio = new JSONObject().put("serverContent", new JSONObject()
                .put("modelTurn", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("inlineData", new JSONObject()
                                        .put("data", modelAudio)
                                        .put("mimeType", "audio/pcm;rate=24000"))))));
        session.onText(socket, assistantAudio.toString(), true);

        verify(transport, atLeastOnce()).sendAudio(eq(context.streamSid()), anyString());
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

    private static String generationComplete() {
        return new JSONObject()
                .put("serverContent", new JSONObject().put("generationComplete", true))
                .toString();
    }

    private static String generationCompleteWithOutput(String text) {
        return new JSONObject()
                .put("serverContent", new JSONObject()
                        .put("outputTranscription", new JSONObject().put("text", text))
                        .put("generationComplete", true))
                .toString();
    }

    private static String toolCall(String id, String name) {
        return toolCall(id, name, new JSONObject());
    }

    private static String toolCall(String id, String name, JSONObject args) {
        return new JSONObject()
                .put("toolCall", new JSONObject()
                        .put("functionCalls", new JSONArray().put(new JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("args", args))))
                .toString();
    }

    private static boolean hasFunction(JSONArray declarations, String name) {
        for (int i = 0; i < declarations.length(); i++) {
            if (name.equals(declarations.getJSONObject(i).optString("name"))) return true;
        }
        return false;
    }
}