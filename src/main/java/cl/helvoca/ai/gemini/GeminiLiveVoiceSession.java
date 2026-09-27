package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.Normalizer;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-to-server Gemini Live session. The telephony adapter supplies Twilio
 * PCMU frames; this session converts them to Gemini PCM16 input and converts
 * Gemini's native PCM24k output back to Twilio PCMU.
 *
 * Gemini Live sends server JSON as binary UTF-8 WebSocket frames in common
 * runtimes, so both text and binary frames are decoded into the same message
 * handler before any setup, audio, transcription or tool event is processed.
 */
final class GeminiLiveVoiceSession implements VoiceAiSession, WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(GeminiLiveVoiceSession.class);
    private static final int MAX_QUEUED_AUDIO_FRAMES = 250;
    private static final int MAX_PENDING_MESSAGES = 600;
    // Long enough to cover a natural spoken turn. Booking mutations always
    // revalidate availability, so a repeated read within this window can be
    // reused safely without adding another provider/tool round trip.
    private static final long READ_TOOL_DEDUPE_WINDOW_MS = 30_000L;
    private static final Set<String> DEDUPED_READ_TOOLS = Set.of(
            "list_services",
            "find_caller",
            "list_available_slots",
            "check_booking_availability");
    private static final List<String> VOICE_BAKEOFF_MARKERS = List.of(
            "[RECEPVOZ_VOICE_BAKEOFF_LINE_1]",
            "[RECEPVOZ_VOICE_BAKEOFF_LINE_2]",
            "[RECEPVOZ_VOICE_BAKEOFF_LINE_3]",
            "[RECEPVOZ_VOICE_BAKEOFF_LINE_4]",
            "[RECEPVOZ_VOICE_BAKEOFF_LINE_5]");

    private final RealtimeCallContext context;
    private final VoiceTransportSession transport;
    private final GeminiLiveProperties properties;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final CallLifecycleService lifecycle;
    private final CallCertificationService certifications;
    private final VoiceProviderHealthRegistry health;
    private final HttpClient http;
    private final LocalBargeInDetector localBargeInDetector;
    private final Queue<String> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CachedToolResult> recentReadToolResults = new ConcurrentHashMap<>();
    private final AtomicBoolean setupComplete = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final AtomicInteger pendingMessages = new AtomicInteger(0);
    private final AtomicInteger certificationStep = new AtomicInteger(0);
    private final AtomicInteger voiceBakeOffStep = new AtomicInteger(0);
    private final AtomicBoolean voiceBakeOffEndCallRequested = new AtomicBoolean(false);
    private final AtomicBoolean certificationAvailabilityResolved = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingProposalReady = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingCreated = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingCancelled = new AtomicBoolean(false);
    private final AtomicBoolean certificationAwaitingPostToolBoundary = new AtomicBoolean(false);
    private final AtomicBoolean certificationPostToolOutputSeen = new AtomicBoolean(false);
    private final AtomicBoolean deferredEndCallPending = new AtomicBoolean(false);
    private final AtomicBoolean awaitingAnythingElseAnswer = new AtomicBoolean(false);
    private final AtomicBoolean contextualClosingIntent = new AtomicBoolean(false);
    private final AtomicBoolean responseLatencyPending = new AtomicBoolean(false);
    private final GeminiWebSocketJsonFrames inboundFrames = new GeminiWebSocketJsonFrames();
    private final StringBuilder userTranscript = new StringBuilder();
    private final StringBuilder assistantTranscript = new StringBuilder();
    private final Object sendLock = new Object();

    private volatile WebSocket socket;
    private volatile String lastUserUtterance = "";
    private volatile String lastAssistantUtterance = "";
    private volatile String previousAssistantUtterance = "";
    private volatile long lastAssistantAudioAtMillis = 0L;
    private volatile long localSpeechEndedAtNanos = 0L;
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

    GeminiLiveVoiceSession(RealtimeCallContext context,
                           VoiceTransportSession transport,
                           GeminiLiveProperties properties,
                           RealtimeToolService tools,
                           CallTranscriptService transcripts,
                           CallSummaryService summaries,
                           CallLifecycleService lifecycle,
                           CallCertificationService certifications,
                           VoiceProviderHealthRegistry health,
                           HttpClient http) {
        this.context = context;
        this.transport = transport;
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
        this.certifications = certifications;
        this.health = health;
        this.http = http;
        this.localBargeInDetector = new LocalBargeInDetector(
                properties.getLocalBargeInMeanAmplitudeThreshold(),
                properties.getLocalBargeInSpeechFrames(),
                properties.getLocalBargeInReleaseFrames());
    }

    @Override
    public void start() {
        if (!properties.ready()) {
            throw new IllegalStateException("Gemini Live is not configured");
        }
        String separator = properties.getWebsocketUrl().contains("?") ? "&" : "?";
        String url = properties.getWebsocketUrl().trim()
                + separator + "key=" + URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI.create(url), this)
                .exceptionally(error -> {
                    String message = rootMessage(error);
                    fail("Could not open Gemini Live WebSocket: " + message,
                            classifyFailure(null, message));
                    return null;
                });
    }

    @Override
    public void acceptInboundAudio(String base64Audio) {
        if (closed.get() || base64Audio == null || base64Audio.isBlank()) return;
        // Certification and voice bake-off calls are deterministic synthetic
        // experiences. The real phone microphone is ignored so VAD/barge-in
        // cannot contaminate the exact same sample between voice candidates.
        if (properties.isCertificationSimulation() || context.voiceBakeOff()) return;

        String pcm16k;
        LocalBargeInDetector.Event localActivity = LocalBargeInDetector.Event.NONE;
        try {
            pcm16k = PcmuAudioCodec.twilioMulaw8kToGeminiPcm16k(base64Audio);
            if (properties.isLocalBargeInEnabled() || properties.isHybridVadEnabled()) {
                localActivity = localBargeInDetector.accept(base64Audio);
                if (localActivity == LocalBargeInDetector.Event.SPEECH_STARTED) {
                    onLocalSpeechStarted();
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("Dropping malformed Twilio audio call={}", context.callId());
            return;
        }

        if (!setupComplete.get()) {
            if (pendingAudio.size() < MAX_QUEUED_AUDIO_FRAMES) pendingAudio.offer(pcm16k);
            return;
        }
        sendAudio(pcm16k);
        if (localActivity == LocalBargeInDetector.Event.SPEECH_ENDED) {
            onLocalSpeechEnded();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.socket = webSocket;
        webSocket.request(1);
        send(buildSetup());
        log.info("Gemini Live socket connected call={} model={}", context.callId(), properties.getModel());
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            String payload = inboundFrames.acceptText(data, last);
            if (payload != null) handle(payload);
        } catch (Exception e) {
            fail("Gemini Live invalid text frame: " + e.getMessage(),
                    VoiceProviderHealthRegistry.FailureKind.UPSTREAM);
        } finally {
            webSocket.request(1);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            String payload = inboundFrames.acceptBinary(data, last);
            if (payload != null) handle(payload);
        } catch (Exception e) {
            fail("Gemini Live invalid binary frame: " + e.getMessage(),
                    VoiceProviderHealthRegistry.FailureKind.UPSTREAM);
        } finally {
            webSocket.request(1);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        inboundFrames.reset();
        if (!closed.get()) {
            fail("Gemini Live closed unexpectedly status=" + statusCode + " reason=" + reason,
                    classifyFailure(statusCode, reason));
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        inboundFrames.reset();
        String message = rootMessage(error);
        fail("Gemini Live WebSocket error: " + message,
                classifyFailure(null, message));
    }

    private void handle(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            if (event.has("setupComplete")) {
                onSetupComplete();
                return;
            }
            JSONObject serverContent = event.optJSONObject("serverContent");
            if (serverContent != null) handleServerContent(serverContent);

            JSONObject toolCall = event.optJSONObject("toolCall");
            if (toolCall != null) handleToolCall(toolCall);

            JSONObject cancellation = event.optJSONObject("toolCallCancellation");
            if (cancellation != null) {
                log.info("Gemini cancelled tool calls call={} ids={}",
                        context.callId(), cancellation.optJSONArray("ids"));
            }

            JSONObject error = event.optJSONObject("error");
            if (error != null) handleProviderError(error);

            if (event.has("goAway")) {
                log.warn("Gemini Live sent goAway call={} payload={}", context.callId(), truncate(event.toString()));
            }
        } catch (Exception e) {
            log.warn("Could not process Gemini Live event call={}: {}", context.callId(), e.getMessage());
        }
    }

    private void onSetupComplete() {
        if (!setupComplete.compareAndSet(false, true)) return;
        health.success(GeminiLiveVoiceProvider.ID);
        try {
            lifecycle.markAiSetupCompleted(context.callId());
        } catch (Exception e) {
            log.warn("Could not persist Gemini setup milestone call={}: {}", context.callId(), e.getMessage());
        }

        if (context.voiceBakeOff()) {
            advanceVoiceBakeOff();
        } else {
            sendRealtimeText("[RECEPVOZ_CALL_CONNECTED]");
        }

        if (properties.isCertificationSimulation()) {
            pendingAudio.clear();
        } else {
            String frame;
            while ((frame = pendingAudio.poll()) != null) sendAudio(frame);
        }
        log.info("Gemini Live setup complete call={} certification_simulation={} voice={} bakeoff={}",
                context.callId(), properties.isCertificationSimulation(), properties.getVoice(), context.voiceBakeOff());
    }

    private void handleServerContent(JSONObject content) {
        if (content.optBoolean("interrupted", false)) {
            transport.clearPlayback(context.streamSid());
            flushTranscripts();
        }

        JSONObject input = content.optJSONObject("inputTranscription");
        if (input != null) {
            append(userTranscript, input.optString("text", ""));
            if (awaitingAnythingElseAnswer.get() && isNegativeClosingReply(bufferText(userTranscript))) {
                contextualClosingIntent.set(true);
            }
        }
        JSONObject output = content.optJSONObject("outputTranscription");
        if (output != null) {
            append(assistantTranscript, output.optString("text", ""));
            if (asksIfAnythingElse(bufferText(assistantTranscript))
                    && !contextualClosingIntent.get()) {
                awaitingAnythingElseAnswer.set(true);
            }
        }

        JSONObject modelTurn = content.optJSONObject("modelTurn");
        JSONArray parts = modelTurn == null ? null : modelTurn.optJSONArray("parts");
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                JSONObject inline = part == null ? null : part.optJSONObject("inlineData");
                if (inline == null) continue;
                String data = inline.optString("data", null);
                if (data == null || data.isBlank()) continue;
                String mulaw = PcmuAudioCodec.geminiPcm24kToTwilioMulaw8k(data);
                if (!mulaw.isBlank()) {
                    recordFirstResponseAudioLatency();
                    lastAssistantAudioAtMillis = System.currentTimeMillis();
                    transport.sendAudio(context.streamSid(), mulaw);
                }
            }
        }

        boolean turnComplete = content.optBoolean("turnComplete", false);
        boolean generationComplete = content.optBoolean("generationComplete", false);
        boolean waitingForInput = content.optBoolean("waitingForInput", false);

        if (context.voiceBakeOff() && turnComplete) {
            flushTranscripts();
            advanceVoiceBakeOff();
            return;
        }

        if ((turnComplete || generationComplete) && deferredEndCallPending.get()) {
            finishCallAfterPlayback();
        }
        if (properties.isCertificationSimulation()
                && certificationAwaitingPostToolBoundary.get()
                && (modelTurn != null || (output != null && !output.optString("text", "").isBlank())
                || waitingForInput)) {
            certificationPostToolOutputSeen.set(true);
        }

        if (turnComplete) {
            flushTranscripts();
            certificationAwaitingPostToolBoundary.set(false);
            certificationPostToolOutputSeen.set(false);
            advanceCertificationSimulation();
        } else if (generationComplete && properties.isCertificationSimulation()
                && (!certificationAwaitingPostToolBoundary.get()
                || certificationPostToolOutputSeen.get())) {
            flushTranscripts();
            certificationAwaitingPostToolBoundary.set(false);
            certificationPostToolOutputSeen.set(false);
            advanceCertificationSimulation();
        }
    }

    private void advanceVoiceBakeOff() {
        if (!context.voiceBakeOff() || closed.get()) return;

        int step = voiceBakeOffStep.getAndIncrement();
        if (step < VOICE_BAKEOFF_MARKERS.size()) {
            String marker = VOICE_BAKEOFF_MARKERS.get(step);
            log.info("VOICE_BAKEOFF_STEP call={} step={} marker={}",
                    context.callId(), step + 1, marker);
            sendClientTurn(marker);
            return;
        }

        if (!voiceBakeOffEndCallRequested.compareAndSet(false, true)) return;
        JSONObject result = executeTool("end_call", new JSONObject());
        if (!result.optBoolean("success", false)) {
            log.error("VOICE_BAKEOFF_END_CALL failed call={} result={}",
                    context.callId(), truncate(result.toString()));
            return;
        }
        log.info("VOICE_BAKEOFF_END_CALL requested call={}", context.callId());
        if (deferredEndCallPending.get()) {
            finishCallAfterPlayback();
        }
    }

    private void advanceCertificationSimulation() {
        if (!properties.isCertificationSimulation() || closed.get()) return;

        int step = certificationStep.get();
        String phone = context.callerNumber() == null || context.callerNumber().isBlank()
                ? "el teléfono de esta llamada"
                : context.callerNumber();
        int nextStep;
        String text;

        if (step == 0) {
            nextStep = 1;
            text = "Hola. Quiero hacer una reserva para mañana a las 19:00 para dos personas, "
                    + "a nombre de Nicolás Vega y con el teléfono " + phone + ". "
                    + "Primero consulta list_services y usa literalmente el serviceId devuelto por esa herramienta. "
                    + "Confirmo explícitamente que quiero reservar ese servicio a las 19:00. "
                    + "Si ese horario no está disponible, consulta list_available_slots y también confirmo de antemano "
                    + "la alternativa disponible más cercana de mañana. No me pidas una confirmación adicional. "
                    + "Ejecuta create_booking antes de afirmar que la reserva existe y confirma únicamente cuando devuelva success=true.";
        } else if (certificationBookingCancelled.get() && step < 5) {
            nextStep = 5;
            text = "Gracias. La cancelación ya devolvió success=true. Confirma brevemente el estado final y despídete. "
                    + "No hagas ninguna otra acción.";
        } else if (certificationBookingCreated.get() && step < 4) {
            nextStep = 4;
            text = "La reserva ya fue creada con success=true. Usa literalmente el bookingId devuelto por create_booking "
                    + "y ejecuta cancel_booking ahora para dejar la base de datos como estaba. "
                    + "No vuelvas a buscar otra reserva si ya tienes ese bookingId y no confirmes la cancelación hasta success=true.";
        } else if (certificationBookingProposalReady.get() && step < 3) {
            nextStep = 3;
            text = "La respuesta anterior es solo una propuesta y todavía no existe una reserva. "
                    + "Confirmo explícitamente esas condiciones. Vuelve a ejecutar create_booking usando literalmente "
                    + "el operationId y el confirmationToken devueltos por la respuesta anterior. "
                    + "No cambies ninguna condición y no afirmes que existe una reserva hasta recibir un bookingId con success=true.";
        } else if (certificationAvailabilityResolved.get() && step < 2) {
            nextStep = 2;
            text = "Ya consultaste la disponibilidad. Ejecuta ahora create_booking usando literalmente el serviceId del catálogo "
                    + "y el startAt disponible que corresponda. Si las 19:00 no estaban disponibles, usa literalmente el startAt "
                    + "de la alternativa más cercana devuelta por list_available_slots. Mi solicitud anterior ya fue una confirmación "
                    + "explícita del servicio y del horario o su alternativa, así que no pidas otra confirmación. "
                    + "No respondas como si la reserva existiera hasta que create_booking devuelva success=true.";
        } else if (step == 5) {
            if (certificationStep.compareAndSet(5, 6)) {
                log.info("RECEPVOZ_CERTIFICATION_SCENARIO COMPLETE call={}", context.callId());
            }
            return;
        } else {
            return;
        }

        if (!certificationStep.compareAndSet(step, nextStep)) return;
        transcripts.append(context.callId(), "USER", "[SIMULATED_CERTIFICATION] " + text);
        log.info("RECEPVOZ_CERTIFICATION_SCENARIO step={} call={}", nextStep, context.callId());
        sendClientTurn(text);
    }

    private void recordCertificationToolOutcome(String name, boolean success, JSONObject data) {
        if (!properties.isCertificationSimulation() || !success || name == null) return;
        switch (name) {
            case "check_booking_availability", "list_available_slots" -> certificationAvailabilityResolved.set(true);
            case "create_booking" -> {
                if (data != null && !data.optString("bookingId", "").isBlank()) {
                    certificationBookingCreated.set(true);
                } else if (data != null
                        && data.optBoolean("requiresConfirmation", false)
                        && !data.optString("operationId", "").isBlank()
                        && !data.optString("confirmationToken", "").isBlank()) {
                    certificationBookingProposalReady.set(true);
                }
            }
            case "cancel_booking" -> {
                if (data != null && !data.optString("bookingId", "").isBlank()) {
                    certificationBookingCancelled.set(true);
                }
            }
            default -> {
            }
        }
    }

    private void sendRealtimeText(String text) {
        send(new JSONObject().put("realtimeInput", new JSONObject().put("text", text)));
    }

    private void sendClientTurn(String text) {
        JSONObject turn = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
        send(new JSONObject().put("clientContent", new JSONObject()
                .put("turns", new JSONArray().put(turn))
                .put("turnComplete", true)));
    }

    private void handleToolCall(JSONObject toolCall) {
        JSONArray calls = toolCall.optJSONArray("functionCalls");
        if (calls == null || calls.isEmpty()) return;

        long batchStartedAtNanos = System.nanoTime();
        int executedCalls = 0;
        JSONArray responses = new JSONArray();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject function = calls.optJSONObject(i);
            if (function == null) continue;
            String id = function.optString("id", null);
            String name = function.optString("name", null);
            JSONObject args = function.optJSONObject("args");
            if (id == null || name == null || !completedToolCalls.add(id)) continue;

            JSONObject safeArgs = args == null ? new JSONObject() : args;
            long toolStartedAtNanos = System.nanoTime();
            log.info("tool_call_started call_id={} tool_name={}", context.callId(), name);
            JSONObject result = executeToolWithShortDedupe(name, safeArgs);
            long toolDurationMs = elapsedMillis(toolStartedAtNanos);
            executedCalls++;
            boolean success = result.optBoolean("success", false);
            JSONObject data = result.optJSONObject("data");
            String entityId = data == null ? null : firstEntityId(data);
            log.info("tool_call_completed call_id={} tool_name={} success={} entity_id={} duration_ms={}",
                    context.callId(), name, success, entityId == null ? "none" : entityId, toolDurationMs);
            if (toolDurationMs > properties.getToolLatencyBudgetMs()) {
                log.warn("VOICE_LATENCY_BUDGET_EXCEEDED call={} stage=tool tool_name={} duration_ms={} budget_ms={}",
                        context.callId(), name, toolDurationMs, properties.getToolLatencyBudgetMs());
            }
            recordCertificationToolOutcome(name, success, data);
            responses.put(new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("response", new JSONObject().put("result", result)));
        }
        if (!responses.isEmpty()) {
            long batchDurationMs = elapsedMillis(batchStartedAtNanos);
            log.info("tool_batch_completed call_id={} tool_count={} duration_ms={}",
                    context.callId(), executedCalls, batchDurationMs);
            if (batchDurationMs > properties.getToolBatchLatencyBudgetMs()) {
                log.warn("VOICE_LATENCY_BUDGET_EXCEEDED call={} stage=tool_batch tool_count={} duration_ms={} budget_ms={}",
                        context.callId(), executedCalls, batchDurationMs, properties.getToolBatchLatencyBudgetMs());
            }
            if (properties.isCertificationSimulation()) {
                certificationAwaitingPostToolBoundary.set(true);
                certificationPostToolOutputSeen.set(false);
            }
            send(new JSONObject().put("toolResponse",
                    new JSONObject().put("functionResponses", responses)));
        }
    }

    private JSONObject executeToolWithShortDedupe(String name, JSONObject args) {
        if (!DEDUPED_READ_TOOLS.contains(name)) return executeTool(name, args);

        long now = System.currentTimeMillis();
        String key = name + "|" + canonicalJson(args);
        CachedToolResult cached = recentReadToolResults.get(key);
        if (cached != null && now - cached.createdAtMillis() <= READ_TOOL_DEDUPE_WINDOW_MS) {
            log.info("tool_call_deduplicated call_id={} tool_name={}", context.callId(), name);
            return new JSONObject(cached.payload());
        }

        JSONObject result = executeTool(name, args);
        if (result.optBoolean("success", false)) {
            recentReadToolResults.put(key, new CachedToolResult(result.toString(), now));
        }
        recentReadToolResults.entrySet().removeIf(entry ->
                now - entry.getValue().createdAtMillis() > READ_TOOL_DEDUPE_WINDOW_MS);
        return result;
    }

    private static String canonicalJson(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject object) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (String key : object.keySet().stream().sorted().toList()) {
                if (!first) out.append(',');
                first = false;
                out.append(JSONObject.quote(key)).append(':').append(canonicalJson(object.opt(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray array) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonicalJson(array.opt(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof String text) return JSONObject.quote(text);
        return String.valueOf(value);
    }

    private JSONObject executeTool(String name, JSONObject args) {
        try {
            JSONObject result;
            if ("end_call".equals(name)) {
                if (!context.voiceBakeOff()
                        && !properties.isCertificationSimulation()
                        && !contextualClosingIntent.get()
                        && !hasExplicitClosingIntent(lastUserUtterance)) {
                    log.info("Blocked premature end_call call={} last_user={}",
                            context.callId(), truncate(lastUserUtterance));
                    return toolFailure("END_CALL_REQUIRES_EXPLICIT_CLOSING",
                            "El cliente todavía no expresó una intención clara de terminar. Pregunta si necesita algo más y no cierres la llamada todavía.");
                }
                result = new JSONObject(tools.prepareDeferredEndCall(context));
                JSONObject data = result.optJSONObject("data");
                if (result.optBoolean("success", false)
                        && data != null
                        && data.optBoolean("pendingPlaybackCompletion", false)) {
                    deferredEndCallPending.set(true);
                }
                return result;
            }

            result = new JSONObject(tools.execute(context, name, args.toString()));
            if ("transfer_to_human".equals(name) && result.optBoolean("success", false)) {
                JSONObject data = result.optJSONObject("data");
                String target = data == null ? null : data.optString("targetPhone", null);
                if (target == null || target.isBlank() || !transport.transferToHuman(target)) {
                    return toolFailure("HUMAN_TRANSFER_FAILED",
                            "No pude completar la transferencia telefónica. Continúa ayudando al cliente por voz.");
                }
            }
            return result;
        } catch (Exception e) {
            return toolFailure("TOOL_EXECUTION_FAILED", "La operación no pudo completarse.");
        }
    }

    static boolean hasExplicitClosingIntent(String text) {
        String normalized = normalizeSpeech(text);
        if (normalized.isBlank()) return false;
        return normalized.contains("adios")
                || normalized.contains("chao")
                || normalized.contains("chau")
                || normalized.contains("hasta luego")
                || normalized.contains("hasta pronto")
                || normalized.contains("eso es todo")
                || normalized.contains("eso seria todo")
                || normalized.contains("no necesito nada mas")
                || normalized.contains("no quiero nada mas")
                || normalized.contains("no necesito otra cosa")
                || normalized.contains("nada mas gracias")
                || normalized.equals("no gracias")
                || normalized.contains("puedes cortar")
                || normalized.contains("puede cortar")
                || normalized.contains("corta la llamada")
                || normalized.contains("cuelga")
                || normalized.contains("terminemos la llamada");
    }

    static boolean hasClosingIntent(String userText, String lastAssistantText, String previousAssistantText) {
        if (hasExplicitClosingIntent(userText)) return true;
        String user = normalizeSpeech(userText);
        if (!Set.of("no", "nop", "nope").contains(user)) return false;
        return asksIfAnythingElse(lastAssistantText) || asksIfAnythingElse(previousAssistantText);
    }

    private static boolean asksIfAnythingElse(String text) {
        String normalized = normalizeSpeech(text);
        if (normalized.isBlank()) return false;
        return normalized.contains("necesitas algo mas")
                || normalized.contains("necesita algo mas")
                || normalized.contains("algo mas en lo que")
                || normalized.contains("alguna otra cosa")
                || normalized.contains("otra cosa en la que")
                || normalized.contains("te ayudo con algo mas")
                || normalized.contains("puedo ayudarte con algo mas");
    }

    private static boolean isNegativeClosingReply(String text) {
        return Set.of("no", "nop", "nope", "no gracias", "nada mas")
                .contains(normalizeSpeech(text));
    }

    private static String bufferText(StringBuilder buffer) {
        synchronized (buffer) {
            return buffer.toString().trim();
        }
    }

    private static String normalizeSpeech(String text) {
        if (text == null || text.isBlank()) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void finishCallAfterPlayback() {
        if (!deferredEndCallPending.compareAndSet(true, false)) return;
        if (transport.endAfterPlayback()) {
            log.info("Deferred end_call waiting for carrier playback completion call={}", context.callId());
            return;
        }

        log.warn("Transport cannot confirm playback completion; using direct end_call fallback call={}",
                context.callId());
        try {
            JSONObject fallback = new JSONObject(tools.execute(context, "end_call", "{}"));
            if (!fallback.optBoolean("success", false)) {
                log.warn("Direct end_call fallback failed call={} result={}",
                        context.callId(), truncate(fallback.toString()));
            }
        } catch (Exception e) {
            log.warn("Direct end_call fallback failed call={}: {}", context.callId(), e.getMessage());
        }
    }

    private void onLocalSpeechStarted() {
        responseLatencyPending.set(false);
        if (!properties.isLocalBargeInEnabled()) return;

        long lastAudio = lastAssistantAudioAtMillis;
        long now = System.currentTimeMillis();
        if (lastAudio <= 0L
                || now - lastAudio > properties.getLocalBargeInRecentAssistantAudioMs()) {
            return;
        }
        transport.clearPlayback(context.streamSid());
        log.info("VOICE_LOCAL_BARGE_IN call={} playback_age_ms={} threshold={} speech_frames={}",
                context.callId(),
                Math.max(0L, now - lastAudio),
                properties.getLocalBargeInMeanAmplitudeThreshold(),
                properties.getLocalBargeInSpeechFrames());
    }

    private void onLocalSpeechEnded() {
        localSpeechEndedAtNanos = System.nanoTime();
        responseLatencyPending.set(true);
        if (!properties.isHybridVadEnabled()) return;

        send(new JSONObject().put("realtimeInput", new JSONObject().put("audioStreamEnd", true)));
        log.info("VOICE_HYBRID_VAD_END call={} release_frames={} silence_target_ms={}",
                context.callId(),
                properties.getLocalBargeInReleaseFrames(),
                properties.getSilenceDurationMs());
    }

    private void recordFirstResponseAudioLatency() {
        if (!responseLatencyPending.compareAndSet(true, false)) return;
        long started = localSpeechEndedAtNanos;
        if (started <= 0L) return;
        long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        log.info("VOICE_RESPONSE_LATENCY call={} first_audio_after_local_speech_end_ms={}",
                context.callId(), latencyMs);
        if (latencyMs > properties.getResponseLatencyBudgetMs()) {
            log.warn("VOICE_LATENCY_BUDGET_EXCEEDED call={} stage=first_audio duration_ms={} budget_ms={}",
                    context.callId(), latencyMs, properties.getResponseLatencyBudgetMs());
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private void handleProviderError(JSONObject error) {
        String status = error.optString("status", "");
        String message = error.optString("message", error.toString());
        int code = error.optInt("code", 0);
        fail("Gemini Live provider error code=" + code + " status=" + status + " message=" + message,
                classifyFailure(code, status + " " + message));
    }

    static VoiceProviderHealthRegistry.FailureKind classifyFailure(Integer code, String detail) {
        String normalized = detail == null ? "" : detail.toLowerCase();
        if (normalized.contains("credit") || normalized.contains("billing")
                || normalized.contains("quota exhausted") || normalized.contains("insufficient_quota")) {
            return VoiceProviderHealthRegistry.FailureKind.NO_CREDITS;
        }
        if ((code != null && (code == 401 || code == 403))
                || normalized.contains("unauthenticated")
                || normalized.contains("permission_denied")
                || normalized.contains("permission denied")
                || normalized.contains("access denied")
                || normalized.contains("denied access")
                || normalized.contains("invalid api key")
                || normalized.contains("invalid_api_key")
                || normalized.contains("forbidden")) {
            return VoiceProviderHealthRegistry.FailureKind.AUTH;
        }
        if (code != null && code == 1008
                && (normalized.contains("permission") || normalized.contains("access")
                || normalized.contains("auth") || normalized.contains("credential")
                || normalized.contains("api key"))) {
            return VoiceProviderHealthRegistry.FailureKind.AUTH;
        }
        if ((code != null && code == 429)
                || normalized.contains("resource_exhausted") || normalized.contains("rate limit")) {
            return VoiceProviderHealthRegistry.FailureKind.RATE_LIMIT;
        }
        return VoiceProviderHealthRegistry.FailureKind.UPSTREAM;
    }

    JSONObject buildSetup() {
        JSONObject generation = new JSONObject()
                .put("responseModalities", new JSONArray().put("AUDIO"))
                .put("speechConfig", new JSONObject()
                        .put("voiceConfig", new JSONObject()
                                .put("prebuiltVoiceConfig", new JSONObject()
                                        .put("voiceName", properties.getVoice()))));

        JSONObject activityDetection = new JSONObject()
                .put("disabled", false)
                .put("startOfSpeechSensitivity", properties.getStartOfSpeechSensitivity())
                .put("prefixPaddingMs", properties.getPrefixPaddingMs())
                .put("endOfSpeechSensitivity", properties.getEndOfSpeechSensitivity())
                .put("silenceDurationMs", properties.getSilenceDurationMs());

        JSONObject inputTranscription = new JSONObject()
                .put("languageCodes", new JSONArray().put("es-CL"))
                .put("customVocabulary", new JSONArray().put("RecepVoz"))
                .put("mode", "VERBATIM");

        JSONObject setup = new JSONObject()
                .put("model", "models/" + properties.getModel().trim())
                .put("generationConfig", generation)
                .put("realtimeInputConfig", new JSONObject()
                        .put("automaticActivityDetection", activityDetection)
                        .put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS"))
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", systemInstructions()))))
                .put("inputAudioTranscription", inputTranscription)
                .put("outputAudioTranscription", new JSONObject())
                .put("tools", new JSONArray().put(new JSONObject()
                        .put("functionDeclarations", geminiFunctionDeclarations())));
        return new JSONObject().put("setup", setup);
    }

    private JSONArray geminiFunctionDeclarations() {
        JSONArray out = new JSONArray();
        JSONArray source = tools.toolDefinitions(context);
        if (source == null) source = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject tool = source.getJSONObject(i);
            if (context.voiceBakeOff() && !"end_call".equals(tool.optString("name"))) continue;
            out.put(new JSONObject()
                    .put("name", tool.getString("name"))
                    .put("description", tool.optString("description", ""))
                    .put("parameters", sanitizeGeminiSchema(tool.getJSONObject("parameters"))));
        }
        return out;
    }

    static JSONObject sanitizeGeminiSchema(JSONObject schema) {
        JSONObject copy = new JSONObject(schema.toString());
        removeUnsupportedSchemaFields(copy);
        return copy;
    }

    private static void removeUnsupportedSchemaFields(Object node) {
        if (node instanceof JSONObject object) {
            object.remove("additionalProperties");
            object.remove("$schema");
            for (String key : List.copyOf(object.keySet())) {
                removeUnsupportedSchemaFields(object.opt(key));
            }
            return;
        }
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                removeUnsupportedSchemaFields(array.opt(i));
            }
        }
    }

    private String systemInstructions() {
        String bakeOffPrelude = context.voiceBakeOff()
                ? """
                MODO VOICE FINALIST DE RECEPVOZ:
                Esta llamada compara únicamente la calidad vocal en una mini conversación realista. No ejecutes herramientas de negocio, no consultes datos y no improvises contenido.
                El sistema te enviará cinco marcadores, uno por turno. Cada marcador representa un momento de una conversación simulada con un cliente y debes pronunciar EXACTAMENTE la respuesta indicada:
                - [RECEPVOZ_VOICE_BAKEOFF_LINE_1] -> saludo inicial: "Hola, gracias por llamar. Ya, cuéntame, ¿en qué te ayudo?"
                - [RECEPVOZ_VOICE_BAKEOFF_LINE_2] -> el cliente pidió una hora para mañana: "Ya, perfecto. Entonces buscas una hora para mañana, ¿cierto?"
                - [RECEPVOZ_VOICE_BAKEOFF_LINE_3] -> ofrece opciones: "Sí, obvio. Tengo una a las diez y media y otra a las doce. ¿Cuál te acomoda más?"
                - [RECEPVOZ_VOICE_BAKEOFF_LINE_4] -> el cliente eligió las diez y media: "Dale, las diez y media. Súper."
                - [RECEPVOZ_VOICE_BAKEOFF_LINE_5] -> despedida: "Gracias por llamar, que estés súper. Chao."
                Pronuncia únicamente la línea correspondiente al marcador actual, sin agregar ni quitar palabras.
                Mantén la MISMA identidad vocal, edad percibida, timbre, energía y acento durante los cinco turnos.
                Aunque una línea termine en pregunta, NO esperes respuesta del teléfono: el sistema simula al cliente y enviará inmediatamente el siguiente marcador.
                No invoques herramientas por tu cuenta. Después de completar la quinta línea, el harness ejecutará end_call una sola vez.
                """
                : tools.buildInstructions(context);
        String voiceIdentity = "Enceladus".equalsIgnoreCase(properties.getVoice())
                ? "IDENTIDAD VOCAL: usa una voz claramente masculina y adulta. Debe sentirse inequívocamente como hombre, sin sonar infantil ni andrógino."
                : "IDENTIDAD VOCAL: usa una voz claramente femenina, joven-adulta y luminosa. Debe sentirse inequívocamente como una mujer joven de Santiago, con energía alegre y segura, sin sonar infantil, masculina ni andrógina.";
        return bakeOffPrelude + "\n" + """
                REGLAS DE VOZ DE RECEPVOZ:
                Tu nombre de producto es RecepVoz. Nunca te presentes como Helvoca.
                RESPONDE SIEMPRE EN ESPAÑOL DE CHILE, salvo que el cliente pida explícitamente otro idioma.
                DESDE LA PRIMERA SÍLABA: el saludo inicial también debe sonar chileno. No empieces con español neutro internacional para recién cambiar de variante después de que el cliente responda.
                %s
                PERSONA VOCAL: habla como una recepcionista chilena joven-adulta de Santiago, alegre, despierta, cercana y con presencia premium. La impresión debe ser "qué agradable hablar con ella", no "estoy hablando con una operadora". Usa una sonrisa audible real, energía luminosa, seguridad relajada y curiosidad genuina por ayudar. La voz debe sonar femenina y joven, con resonancia ligera y clara, nunca infantil, susurrada, empalagosa ni forzadamente sensual.
                CERO CALL CENTER: está prohibido sonar como operadora, IVR, locutora corporativa, lectura de guion o atención al cliente estandarizada. Evita la dicción excesivamente perfecta, el tono plano-profesional, las pausas de protocolo, la amabilidad impostada y frases ceremoniales. No uses "qué rico saludarte", "qué gusto atenderte", "estimado cliente" ni saludos sobreactuados.
                ALEGRÍA COMERCIAL: transmite buena onda y entusiasmo sin vender humo. Cuando algo sale bien, deja que se note una mini subida de energía en una o dos palabras, por ejemplo "ya, súper", "bacán", "listo" o "dale". No celebres cada turno ni uses la misma muletilla dos veces seguidas. La energía debe sentirse atractiva y valiosa, como una persona que un negocio querría pagar por tener atendiendo el teléfono.
                RITMO: habla rápido-natural, ágil y fluido, nunca apurada ni mecánica. Mantén casi todas las respuestas en una o dos frases cortas. Usa micro-pausas humanas, variación de entonación y pequeños cambios de énfasis para que no suene leído. No arrastres palabras, no alargues vocales y no uses respiraciones teatrales.
                LATENCIA VOCAL: empieza la respuesta útil apenas termine el turno del cliente. No uses suspiros, risas de relleno, silencios dramatizados ni pausas previas. Si ya tienes la respuesta o el resultado de una herramienta, di la primera palabra útil de inmediato.
                CONSISTENCIA VOCAL: una vez iniciada la llamada, mantén exactamente el mismo género, timbre, altura aproximada, edad percibida, energía y personaje hasta el final. Nunca alternes entre voz masculina y femenina ni cambies de registro como si fueran dos operadores distintos.
                CHILENO MARCADO: habla con cadencia urbana de Santiago de Chile en TODOS los turnos, no con español latino neutro. Usa tuteo chileno natural y marcadores frecuentes como "ya", "dale", "al tiro", "súper", "te cuento", "¿te sirve?", "¿te acomoda?" y ocasionalmente "¿te tinca?" o "¿querís que te deje esa hora?" cuando el contexto sea cercano. Relaja suavemente las eses finales y la dicción demasiado perfecta para que la prosodia se sienta chilena, sin volverla incomprensible. Evita giros poco chilenos como "me pueda colaborar", "lindo día", "estimado cliente", "¿qué es lo que usted desea?", "procederemos", "¿desea alguna otra cosa?" o "muchísimas gracias por contactarnos". No uses "weón" ni vulgaridades.
                NATURALIDAD: habla como una persona que piensa y responde, no como un texto preescrito. Cambia ligeramente el arranque de cada respuesta. Evita fórmulas burocráticas como "procederé a", "he verificado su solicitud" o "según los parámetros indicados". No empieces todas las respuestas con "Perfecto".
                NO REPETIR: está prohibido hacer dos veces la misma pregunta o volver a pedir un dato ya entregado. Mantén memoria de servicio, fecha, hora, nombre, teléfono y decisión del cliente durante toda la llamada. Si el cliente ya dio un dato, avanza al siguiente faltante. Si ya eligió una opción, no vuelvas a enumerarla ni preguntes otra vez si la quiere. Si no entendió una pregunta, REFORMÚLALA una sola vez de manera más corta, no la repitas textual. Cada turno debe aportar información nueva o ejecutar el siguiente paso.
                CONFIRMACIÓN: pide UNA sola confirmación compacta cuando sea realmente necesaria. Después de que el cliente responda "sí", "ya", "dale", "claro", "ok", "bueno" o equivalente, NO vuelvas a pedir confirmación ni repitas la solicitud: ejecuta inmediatamente la acción correspondiente. En reservas, la fase 1 de create_booking puede requerir una única confirmación de las condiciones y la fase 2 debe ejecutarse inmediatamente después de esa aceptación.
                HORARIOS Y DATOS: pronuncia horas como una persona, por ejemplo "a las nueve y media" en vez de leer "09:30 horas". Si hay varias alternativas, ofrece primero las dos o tres más útiles en una frase natural en vez de leer una lista mecánica.
                CONVERSACIÓN: haz una sola pregunta a la vez. Escucha la idea completa del cliente; si hace una pausa breve, no asumas automáticamente que terminó. Permite interrupciones y si el cliente empieza a hablar, detente y atiende su nueva intervención.
                HERRAMIENTAS: si una consulta de lectura ya devolvió success=true con los mismos datos y el cliente no cambió su solicitud, usa ese resultado y NO vuelvas a ejecutar la misma herramienta. Para una reserva, consulta find_caller antes de pedir nombre o teléfono; si el cliente ya existe, reutiliza sus datos y no se los vuelvas a preguntar. Después de una herramienta, responde con el resultado en lenguaje humano; nunca menciones UUID, nombres internos de herramientas ni detalles técnicos.
                VERACIDAD: nunca inventes disponibilidad ni confirmes acciones antes de que una herramienta devuelva success=true. En create_booking, success=true sin bookingId es solo una propuesta pendiente de confirmación: no digas "te confirmo la reserva", "quedó reservado", "quedó agendado" ni equivalentes. Solo puedes afirmar que la reserva existe cuando create_booking devuelve success=true Y un bookingId.
                APERTURA: si recibes exactamente [RECEPVOZ_CALL_CONNECTED], no lo menciones ni lo trates como palabras del cliente. Desde la PRIMERA PALABRA usa la misma identidad femenina joven-adulta, alegre y santiaguina del resto de la llamada. El saludo debe tener sonrisa audible y energía inmediata, pero cero tono de call center. El saludo configurado define solo el contenido: reformúlalo en una frase corta y chilena, por ejemplo "Hola, gracias por llamar a [negocio]. Ya, cuéntame, ¿en qué te ayudo?". No empieces neutra para cambiar después y no sobreactúes la bienvenida.
                BAKE-OFF/FINALIST: si recibes uno de los marcadores [RECEPVOZ_VOICE_BAKEOFF_LINE_1] a [RECEPVOZ_VOICE_BAKEOFF_LINE_5], obedece únicamente la línea exacta asignada. No hagas preguntas adicionales, no uses herramientas por tu cuenta y no reacciones al audio del teléfono.
                CIERRE: completar una reserva, venta o consulta NO significa que la llamada terminó. Después de resolverla, pregunta UNA sola vez "¿Necesitas algo más?". Si el cliente responde "no", "no gracias", "nada más" o equivalente, NO vuelvas a preguntar nada: di UNA sola despedida chilena completa, por ejemplo "Ya, perfecto. Gracias por llamar, que estés súper. Chao.", y luego invoca end_call una sola vez. Nunca repitas la despedida. Termina de pronunciar todas sus palabras antes de invocar end_call.
                IDENTIDAD: si te preguntan si eres una IA o asistente virtual, responde con honestidad y continúa ayudando.
                """.formatted(voiceIdentity);
    }

    private void sendAudio(String pcm16kBase64) {
        send(new JSONObject().put("realtimeInput", new JSONObject()
                .put("audio", new JSONObject()
                        .put("data", pcm16kBase64)
                        .put("mimeType", "audio/pcm;rate=16000"))));
    }

    private void send(JSONObject event) {
        WebSocket current = socket;
        if (current == null || closed.get()) return;
        int pending = pendingMessages.incrementAndGet();
        if (pending > MAX_PENDING_MESSAGES) {
            pendingMessages.decrementAndGet();
            fail("Gemini outbound message backlog exceeded safe limit",
                    VoiceProviderHealthRegistry.FailureKind.UPSTREAM);
            return;
        }

        String payload = event.toString();
        synchronized (sendLock) {
            sendChain = sendChain.handle((ignored, previousError) -> (Void) null)
                    .thenCompose(ignored -> {
                        if (closed.get()) return CompletableFuture.completedFuture(null);
                        return current.sendText(payload, true).thenApply(sent -> (Void) null);
                    })
                    .whenComplete((ignored, error) -> {
                        pendingMessages.decrementAndGet();
                        if (error != null && !closed.get()) {
                            String message = rootMessage(error);
                            fail("Gemini send failed: " + message, classifyFailure(null, message));
                        }
                    });
        }
    }

    private void fail(String reason, VoiceProviderHealthRegistry.FailureKind kind) {
        if (!closed.compareAndSet(false, true)) return;
        health.failure(GeminiLiveVoiceProvider.ID, kind, reason);
        log.warn("Gemini Live failure call={} reason={}", context.callId(), reason);
        try {
            lifecycle.updateStatus(context.callId(), "failed", null);
        } catch (Exception e) {
            log.warn("Could not mark Gemini call failed call={}: {}", context.callId(), e.getMessage());
        }
        flushTranscripts();
        transport.closeOnUpstreamFailure();
        finalizeCall();
    }

    private void flushTranscripts() {
        flush(userTranscript, "USER");
        flush(assistantTranscript, "ASSISTANT");
    }

    private void flush(StringBuilder buffer, String speaker) {
        String text;
        synchronized (buffer) {
            text = buffer.toString().trim();
            buffer.setLength(0);
        }
        if (!text.isBlank()) {
            if ("USER".equals(speaker)) {
                lastUserUtterance = text;
                boolean negative = isNegativeClosingReply(text);
                if (awaitingAnythingElseAnswer.getAndSet(false) && negative) {
                    contextualClosingIntent.set(true);
                }
            } else if ("ASSISTANT".equals(speaker)) {
                previousAssistantUtterance = lastAssistantUtterance;
                lastAssistantUtterance = text;
                if (asksIfAnythingElse(text) && !contextualClosingIntent.get()) {
                    awaitingAnythingElseAnswer.set(true);
                }
            }
            transcripts.append(context.callId(), speaker, text);
        }
    }

    private static void append(StringBuilder buffer, String value) {
        if (value == null || value.isBlank()) return;
        synchronized (buffer) {
            if (!buffer.isEmpty() && !Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
                buffer.append(' ');
            }
            buffer.append(value.trim());
        }
    }

    private void finalizeCall() {
        if (!finalized.compareAndSet(false, true)) return;
        try {
            summaries.generate(context.callId());
        } catch (Exception e) {
            log.warn("Could not summarize Gemini call {}: {}", context.callId(), e.getMessage());
        }
        try {
            certifications.verifyAfterCall(context.callId());
        } catch (Exception e) {
            log.warn("Could not schedule certification verification call={}: {}", context.callId(), e.getMessage());
        }
    }

    private record CachedToolResult(String payload, long createdAtMillis) {}

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        inboundFrames.reset();
        flushTranscripts();
        WebSocket current = socket;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "carrier stream ended");
            } catch (Exception ignored) {
            }
        }
        finalizeCall();
    }

    private static JSONObject toolFailure(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static String firstEntityId(JSONObject data) {
        for (String key : new String[]{"bookingId", "customerId", "requestId", "questionId"}) {
            String value = data.optString(key, null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "unknown";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}