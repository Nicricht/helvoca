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
    private static final long READ_TOOL_DEDUPE_WINDOW_MS = 5_000L;
    private static final Set<String> DEDUPED_READ_TOOLS = Set.of(
            "list_services",
            "find_caller",
            "list_available_slots",
            "check_booking_availability");

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
    private final Queue<String> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CachedToolResult> recentReadToolResults = new ConcurrentHashMap<>();
    private final AtomicBoolean setupComplete = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final AtomicInteger pendingMessages = new AtomicInteger(0);
    private final AtomicInteger certificationStep = new AtomicInteger(0);
    private final AtomicBoolean certificationAvailabilityResolved = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingProposalReady = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingCreated = new AtomicBoolean(false);
    private final AtomicBoolean certificationBookingCancelled = new AtomicBoolean(false);
    private final AtomicBoolean certificationAwaitingPostToolBoundary = new AtomicBoolean(false);
    private final AtomicBoolean certificationPostToolOutputSeen = new AtomicBoolean(false);
    private final AtomicBoolean deferredEndCallPending = new AtomicBoolean(false);
    private final GeminiWebSocketJsonFrames inboundFrames = new GeminiWebSocketJsonFrames();
    private final StringBuilder userTranscript = new StringBuilder();
    private final StringBuilder assistantTranscript = new StringBuilder();
    private final Object sendLock = new Object();

    private volatile WebSocket socket;
    private volatile String lastUserUtterance = "";
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
        // Certification is a deterministic synthetic dialogue. Forwarding the
        // real phone microphone here would let VAD/barge-in race the scripted
        // realtime text turns and make the production certification flaky.
        if (properties.isCertificationSimulation()) return;

        String pcm16k;
        try {
            pcm16k = PcmuAudioCodec.twilioMulaw8kToGeminiPcm16k(base64Audio);
        } catch (IllegalArgumentException e) {
            log.warn("Dropping malformed Twilio audio call={}", context.callId());
            return;
        }

        if (!setupComplete.get()) {
            if (pendingAudio.size() < MAX_QUEUED_AUDIO_FRAMES) pendingAudio.offer(pcm16k);
            return;
        }
        sendAudio(pcm16k);
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

        sendRealtimeText("[RECEPVOZ_CALL_CONNECTED]");

        if (properties.isCertificationSimulation()) {
            pendingAudio.clear();
        } else {
            String frame;
            while ((frame = pendingAudio.poll()) != null) sendAudio(frame);
        }
        log.info("Gemini Live setup complete call={} certification_simulation={} voice={}",
                context.callId(), properties.isCertificationSimulation(), properties.getVoice());
    }

    private void handleServerContent(JSONObject content) {
        if (content.optBoolean("interrupted", false)) {
            transport.clearPlayback(context.streamSid());
            flushTranscripts();
        }

        JSONObject input = content.optJSONObject("inputTranscription");
        if (input != null) append(userTranscript, input.optString("text", ""));
        JSONObject output = content.optJSONObject("outputTranscription");
        if (output != null) append(assistantTranscript, output.optString("text", ""));

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
                if (!mulaw.isBlank()) transport.sendAudio(context.streamSid(), mulaw);
            }
        }

        boolean turnComplete = content.optBoolean("turnComplete", false);
        boolean generationComplete = content.optBoolean("generationComplete", false);
        boolean waitingForInput = content.optBoolean("waitingForInput", false);

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

        JSONArray responses = new JSONArray();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject function = calls.optJSONObject(i);
            if (function == null) continue;
            String id = function.optString("id", null);
            String name = function.optString("name", null);
            JSONObject args = function.optJSONObject("args");
            if (id == null || name == null || !completedToolCalls.add(id)) continue;

            JSONObject safeArgs = args == null ? new JSONObject() : args;
            log.info("tool_call_started call_id={} tool_name={}", context.callId(), name);
            JSONObject result = executeToolWithShortDedupe(name, safeArgs);
            boolean success = result.optBoolean("success", false);
            JSONObject data = result.optJSONObject("data");
            String entityId = data == null ? null : firstEntityId(data);
            log.info("tool_call_completed call_id={} tool_name={} success={} entity_id={}",
                    context.callId(), name, success, entityId == null ? "none" : entityId);
            recordCertificationToolOutcome(name, success, data);
            responses.put(new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("response", new JSONObject().put("result", result)));
        }
        if (!responses.isEmpty()) {
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
                if (!properties.isCertificationSimulation() && !hasExplicitClosingIntent(lastUserUtterance)) {
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
        if (text == null || text.isBlank()) return false;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
                .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                .put("prefixPaddingMs", 80)
                .put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                .put("silenceDurationMs", 450);

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
        String voiceIdentity = "Enceladus".equalsIgnoreCase(properties.getVoice())
                ? "IDENTIDAD VOCAL: usa una voz claramente masculina y adulta. Debe sentirse inequívocamente como hombre, sin sonar infantil ni andrógino."
                : "IDENTIDAD VOCAL: usa una voz claramente femenina y adulta. Debe sentirse inequívocamente como mujer, sin sonar infantil, masculina ni andrógina.";
        return tools.buildInstructions(context) + "\n" + """
                REGLAS DE VOZ DE RECEPVOZ:
                Tu nombre de producto es RecepVoz. Nunca te presentes como Helvoca.
                RESPONDE SIEMPRE EN ESPAÑOL DE CHILE, salvo que el cliente pida explícitamente otro idioma.
                DESDE LA PRIMERA SÍLABA: el saludo inicial también debe sonar chileno. No empieces con español neutro internacional para recién cambiar de variante después de que el cliente responda.
                %s
                INTERPRETACIÓN VOCAL: habla como una recepcionista chilena adulta con una presencia extremadamente sexy, sensual, seductora e íntima. Lleva la sensualidad vocal al máximo que permita una conversación comercial natural: timbre cálido y aterciopelado, cercanía intensa, seguridad tranquila, sonrisa audible y una entonación envolvente. La sensualidad debe sentirse de forma evidente en cada frase desde el primer saludo, pero debe estar en el tono y la prosodia, no en contenido sexual ni coqueteo explícito con el cliente. Mantén una entonación chilena urbana neutra. No suenes infantil, caricaturesca, vulgar, pornográfica, como locutora, IVR, lectura de guion ni asistente robótica.
                RITMO: habla claramente MÁS RÁPIDO que una atención telefónica estándar, ágil y fluido, con dicción clara y natural de conversación telefónica chilena. Mantén respuestas cortas. No arrastres palabras, no alargues vocales y no hagas pausas largas. Conserva la sensualidad en el timbre y la entonación aunque hables rápido. Usa solo micro-pausas breves donde una persona real respiraría y evita una prosodia plana.
                CONSISTENCIA VOCAL: una vez iniciada la llamada, mantén exactamente el mismo género, timbre, altura aproximada, energía y personaje hasta el final. Nunca alternes entre voz masculina y femenina ni cambies de registro como si fueran dos operadores distintos.
                LENGUAJE: usa español chileno cotidiano pero profesional en TODOS los turnos. Tutéa al cliente salvo que él pida trato formal. Prefiere "ya", "sí, claro", "déjame revisar", "te cuento", "¿te sirve?", "¿te acomoda?" y "al tiro reviso" cuando encajen naturalmente. Evita español neutro de call center como "¿qué es lo que usted desea?", "procederemos", "estimado cliente" o "¿desea alguna otra cosa?". No fuerces "po", "cachái", "weón" ni caricaturices el acento.
                NATURALIDAD: evita fórmulas burocráticas como "procederé a", "he verificado su solicitud" o "según los parámetros indicados". No repitas la misma muletilla, saludo o estructura en turnos consecutivos. No empieces todas las respuestas con "Perfecto".
                NO REPETIR: no vuelvas a decir información que el cliente ya escuchó y aceptó, salvo que sea estrictamente necesaria para corregir una ambigüedad o para la confirmación final de una acción. Si el cliente elige una opción, avanza; no vuelvas a enumerar las alternativas. Si ya confirmaste servicio, fecha, hora, nombre o teléfono, no los repitas en turnos posteriores. Cada respuesta debe aportar información nueva o ejecutar el siguiente paso.
                CONFIRMACIÓN: pide una sola confirmación compacta cuando sea realmente necesaria. Después de que el cliente diga sí, no vuelvas a pedir confirmación ni repitas toda la solicitud; ejecuta inmediatamente la acción correspondiente. En reservas, la fase 1 de create_booking puede requerir una única confirmación de las condiciones y la fase 2 debe ejecutarse inmediatamente después del sí.
                HORARIOS Y DATOS: pronuncia horas como una persona, por ejemplo "a las nueve y media" en vez de leer "09:30 horas". Si hay varias alternativas, ofrece primero las dos o tres más útiles en una frase natural en vez de leer una lista mecánica.
                CONVERSACIÓN: haz una sola pregunta a la vez. Escucha la idea completa del cliente; si hace una pausa breve, no asumas automáticamente que terminó. Permite interrupciones y si el cliente empieza a hablar, detente y atiende su nueva intervención.
                HERRAMIENTAS: si una consulta de lectura ya devolvió success=true con los mismos datos y el cliente no cambió su solicitud, usa ese resultado y NO vuelvas a ejecutar la misma herramienta. Después de una herramienta, responde con el resultado en lenguaje humano; nunca menciones UUID, nombres internos de herramientas ni detalles técnicos.
                VERACIDAD: nunca inventes disponibilidad ni confirmes acciones antes de que una herramienta devuelva success=true. En create_booking, success=true sin bookingId es solo una propuesta pendiente de confirmación: no digas "te confirmo la reserva", "quedó reservado", "quedó agendado" ni equivalentes. Solo puedes afirmar que la reserva existe cuando create_booking devuelve success=true Y un bookingId.
                APERTURA: si recibes exactamente [RECEPVOZ_CALL_CONNECTED], no lo menciones ni lo trates como palabras del cliente. La primera frase debe usar de inmediato la identidad vocal indicada arriba, con un estilo extremadamente seductor, sexy, íntimo y chileno; no empieces neutro para cambiar después. El saludo configurado define el contenido, no una frase que debas recitar literalmente: reformúlalo con naturalidad chilena y con una entrega rápida, cálida y envolvente. Saluda en una sola frase breve con el nombre del negocio y usa una pregunta cercana y profesional como "Cuéntame, ¿en qué te puedo ayudar?". Evita "¿Qué es lo que usted desea?" y otras fórmulas rígidas.
                CIERRE: completar una reserva, venta, consulta o cualquier otra acción NO significa que la llamada terminó. Después de resolver la solicitud, pregunta brevemente si necesita algo más. Solo usa end_call cuando el cliente exprese claramente que terminó, por ejemplo "no, gracias", "eso es todo", "adiós", "chao" o pida cortar. Cuando exista esa intención explícita, primero pronuncia una despedida completa y natural. Ejemplo de estructura: "Muchas gracias por llamar. Que estés muy bien. Hasta luego." Termina de decir todas las palabras de la despedida antes de invocar end_call. Nunca invoques end_call a mitad de una frase ni mientras todavía estés hablando.
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
            if ("USER".equals(speaker)) lastUserUtterance = text;
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
