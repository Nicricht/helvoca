package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final AtomicBoolean setupComplete = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final AtomicInteger pendingMessages = new AtomicInteger(0);
    private final AtomicInteger certificationStep = new AtomicInteger(0);
    private final GeminiWebSocketJsonFrames inboundFrames = new GeminiWebSocketJsonFrames();
    private final StringBuilder userTranscript = new StringBuilder();
    private final StringBuilder assistantTranscript = new StringBuilder();
    private final Object sendLock = new Object();

    private volatile WebSocket socket;
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

        sendClientText("[RECEPVOZ_CALL_CONNECTED]");

        String frame;
        while ((frame = pendingAudio.poll()) != null) sendAudio(frame);
        log.info("Gemini Live setup complete call={} certification_simulation={}",
                context.callId(), properties.isCertificationSimulation());
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

        if (content.optBoolean("turnComplete", false)) {
            flushTranscripts();
            advanceCertificationSimulation();
        }
    }

    private void advanceCertificationSimulation() {
        if (!properties.isCertificationSimulation() || closed.get()) return;
        int step = certificationStep.getAndIncrement();
        String phone = context.callerNumber() == null || context.callerNumber().isBlank()
                ? "el teléfono de esta llamada"
                : context.callerNumber();
        String text = switch (step) {
            case 0 -> "Hola. Quiero hacer una reserva para mañana a las 19:00 para dos personas, "
                    + "a nombre de Nicolás Vega y con el teléfono " + phone + ". "
                    + "Primero consulta list_services y usa literalmente el serviceId devuelto por esa herramienta. "
                    + "Si ese horario no está disponible, busca el horario disponible más cercano de mañana y reserva ese. "
                    + "Confirma únicamente después de que la herramienta haya devuelto éxito.";
            case 1 -> "Si la reserva anterior se creó correctamente, dime brevemente sus datos y luego cancélala "
                    + "para dejar la base de datos como estaba. Si no se creó, explica brevemente el motivo sin inventar nada.";
            case 2 -> "Gracias. Confirma brevemente el estado final y despídete. No hagas ninguna otra acción.";
            default -> null;
        };
        if (text == null) {
            log.info("RECEPVOZ_CERTIFICATION_SCENARIO COMPLETE call={}", context.callId());
            return;
        }
        transcripts.append(context.callId(), "USER", "[SIMULATED_CERTIFICATION] " + text);
        log.info("RECEPVOZ_CERTIFICATION_SCENARIO step={} call={}", step + 1, context.callId());
        sendClientText(text);
    }

    private void sendClientText(String text) {
        send(new JSONObject().put("clientContent", new JSONObject()
                .put("turns", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", text)))))
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

            log.info("tool_call_started call_id={} tool_name={}", context.callId(), name);
            JSONObject result = executeTool(name, args == null ? new JSONObject() : args);
            boolean success = result.optBoolean("success", false);
            JSONObject data = result.optJSONObject("data");
            String entityId = data == null ? null : firstEntityId(data);
            log.info("tool_call_completed call_id={} tool_name={} success={} entity_id={}",
                    context.callId(), name, success, entityId == null ? "none" : entityId);
            responses.put(new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("response", new JSONObject().put("result", result)));
        }
        if (!responses.isEmpty()) {
            send(new JSONObject().put("toolResponse",
                    new JSONObject().put("functionResponses", responses)));
        }
    }

    private JSONObject executeTool(String name, JSONObject args) {
        try {
            JSONObject result = new JSONObject(tools.execute(context, name, args.toString()));
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

        JSONObject setup = new JSONObject()
                .put("model", "models/" + properties.getModel().trim())
                .put("generationConfig", generation)
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", systemInstructions()))))
                .put("inputAudioTranscription", new JSONObject())
                .put("outputAudioTranscription", new JSONObject())
                .put("tools", new JSONArray().put(new JSONObject()
                        .put("functionDeclarations", geminiFunctionDeclarations())));
        return new JSONObject().put("setup", setup);
    }

    private JSONArray geminiFunctionDeclarations() {
        JSONArray out = new JSONArray();
        JSONArray source = RealtimeToolDefinitions.all();
        for (int i = 0; i < source.length(); i++) {
            JSONObject tool = source.getJSONObject(i);
            out.put(new JSONObject()
                    .put("name", tool.getString("name"))
                    .put("description", tool.optString("description", ""))
                    .put("parameters", tool.getJSONObject("parameters")));
        }
        return out;
    }

    private String systemInstructions() {
        return tools.buildInstructions(context) + "\n" + """
                REGLAS DE VOZ DE RECEPVOZ:
                Tu nombre de producto es RecepVoz. Nunca te presentes como Helvoca.
                Habla como una recepcionista humana, cálida, natural y breve. Haz una sola pregunta a la vez.
                Permite interrupciones y usa el contexto previo; no repitas preguntas ya contestadas.
                Nunca inventes disponibilidad ni confirmes acciones antes de que una herramienta devuelva success=true.
                Si recibes exactamente el mensaje técnico [RECEPVOZ_CALL_CONNECTED], no lo menciones ni lo trates como palabras del cliente. Saluda brevemente, menciona el nombre del negocio y pregunta en qué puedes ayudar.
                Si el cliente empieza a hablar mientras tú respondes, detente y atiende su nueva intervención.
                Evita usar "Perfecto" de manera repetitiva.
                """;
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
        if (!text.isBlank()) transcripts.append(context.callId(), speaker, text);
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
        if (properties.isCertificationSimulation()) {
            try {
                certifications.verifyAfterCall(context.callId());
            } catch (Exception e) {
                log.warn("Could not schedule certification verification call={}: {}", context.callId(), e.getMessage());
            }
        }
    }

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
