package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.voice.VoiceAiSession;
import cl.helvoca.voice.VoiceTransportSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
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
 * OpenAI implementation of Helvoca's provider-neutral realtime voice session.
 *
 * <p>This class knows OpenAI's protocol, but no longer knows how Twilio encodes
 * outbound media events. That responsibility lives in the telephony adapter.</p>
 */
public final class OpenAiRealtimeBridge implements WebSocket.Listener, VoiceAiSession {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeBridge.class);
    private static final int MAX_QUEUED_AUDIO_FRAMES = 250;
    private static final int MAX_PENDING_OPENAI_MESSAGES = 600;

    private final RealtimeCallContext context;
    private final VoiceTransportSession transport;
    private final OpenAiRealtimeProperties properties;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final HttpClient httpClient;
    private final Queue<String> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger pendingOpenAiMessages = new AtomicInteger(0);
    private final StringBuilder incomingText = new StringBuilder();
    private final Object sendLock = new Object();

    private volatile WebSocket openAiSocket;
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

    OpenAiRealtimeBridge(RealtimeCallContext context,
                         VoiceTransportSession transport,
                         OpenAiRealtimeProperties properties,
                         RealtimeToolService tools,
                         CallTranscriptService transcripts,
                         CallSummaryService summaries,
                         HttpClient httpClient) {
        this.context = context;
        this.transport = transport;
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.httpClient = httpClient;
    }

    @Override
    public void start() {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for realtime calls");
        }
        String url = properties.getRealtimeUrl() + "?model="
                + URLEncoder.encode(properties.getRealtimeModel(), StandardCharsets.UTF_8);
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("OpenAI-Safety-Identifier", context.businessId().toString())
                .buildAsync(URI.create(url), this)
                .exceptionally(error -> {
                    handleUpstreamFailure("Could not open OpenAI Realtime: " + rootMessage(error));
                    return null;
                });
    }

    @Override
    public void acceptInboundAudio(String base64Pcmu) {
        if (closed.get() || base64Pcmu == null || base64Pcmu.isBlank()) return;
        if (open.get()) {
            sendOpenAi(new JSONObject().put("type", "input_audio_buffer.append").put("audio", base64Pcmu));
            return;
        }
        if (pendingAudio.size() < MAX_QUEUED_AUDIO_FRAMES) {
            pendingAudio.offer(base64Pcmu);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.openAiSocket = webSocket;
        this.open.set(true);
        webSocket.request(1);
        sendSessionUpdate();
        String frame;
        while ((frame = pendingAudio.poll()) != null) {
            sendOpenAi(new JSONObject().put("type", "input_audio_buffer.append").put("audio", frame));
        }
        sendGreeting();
        log.info("OpenAI Realtime connected call={} model={}", context.callId(), properties.getRealtimeModel());
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (incomingText) {
            incomingText.append(data);
            if (last) {
                String payload = incomingText.toString();
                incomingText.setLength(0);
                handleOpenAiEvent(payload);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        open.set(false);
        if (!closed.get()) {
            handleUpstreamFailure("OpenAI Realtime closed unexpectedly status=" + statusCode + " reason=" + reason);
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        handleUpstreamFailure("OpenAI Realtime WebSocket error: " + rootMessage(error));
    }

    private void sendSessionUpdate() {
        JSONObject turnDetection = new JSONObject()
                .put("type", "server_vad")
                .put("threshold", 0.5)
                .put("prefix_padding_ms", 300)
                .put("silence_duration_ms", 500)
                .put("create_response", true)
                .put("interrupt_response", true);

        JSONObject input = new JSONObject()
                .put("format", new JSONObject().put("type", "audio/pcmu"))
                .put("transcription", new JSONObject().put("model", properties.getTranscriptionModel()))
                .put("turn_detection", turnDetection);

        JSONObject output = new JSONObject()
                .put("format", new JSONObject().put("type", "audio/pcmu"))
                .put("voice", properties.getVoice());

        JSONObject session = new JSONObject()
                .put("type", "realtime")
                .put("model", properties.getRealtimeModel())
                .put("instructions", tools.buildInstructions(context))
                .put("output_modalities", new JSONArray().put("audio"))
                .put("audio", new JSONObject().put("input", input).put("output", output))
                .put("tools", RealtimeToolDefinitions.all())
                .put("tool_choice", "auto");

        sendOpenAi(new JSONObject().put("type", "session.update").put("session", session));
    }

    private void sendGreeting() {
        JSONObject response = new JSONObject()
                .put("instructions", "Saluda brevemente al cliente, di el nombre del negocio y pregunta en qué puedes ayudar. No afirmes ninguna acción todavía.")
                .put("output_modalities", new JSONArray().put("audio"));
        sendOpenAi(new JSONObject().put("type", "response.create").put("response", response));
    }

    private void handleOpenAiEvent(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String type = event.optString("type", "");
            switch (type) {
                case "response.output_audio.delta" -> forwardAudio(event.optString("delta", null));
                case "input_audio_buffer.speech_started" -> clearPlayback();
                case "conversation.item.input_audio_transcription.completed" ->
                        transcripts.append(context.callId(), "USER", event.optString("transcript", ""));
                case "response.output_audio_transcript.done" ->
                        transcripts.append(context.callId(), "ASSISTANT", event.optString("transcript", ""));
                case "response.output_item.done" -> handleOutputItem(event.optJSONObject("item"));
                case "response.done" -> handleResponseDone(event.optJSONObject("response"));
                case "error" -> logOpenAiError(event);
                default -> { }
            }
        } catch (Exception e) {
            log.warn("Could not process Realtime event for call {}: {}", context.callId(), e.getMessage());
        }
    }

    private void handleResponseDone(JSONObject response) {
        if (response == null) return;
        JSONArray output = response.optJSONArray("output");
        if (output == null) return;
        for (int i = 0; i < output.length(); i++) handleOutputItem(output.optJSONObject(i));
    }

    private void handleOutputItem(JSONObject item) {
        if (item == null || !"function_call".equals(item.optString("type"))) return;
        String callId = item.optString("call_id", null);
        String name = item.optString("name", null);
        String arguments = item.optString("arguments", "{}");
        if (callId == null || name == null || !completedToolCalls.add(callId)) return;

        String result = tools.execute(context, name, arguments);
        if ("transfer_to_human".equals(name)) {
            String transferFailure = executeHumanTransfer(result);
            if (transferFailure == null) {
                finishAiSession("Transferred to human");
                return;
            }
            result = transferFailure;
        }

        JSONObject outputItem = new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", result);
        sendOpenAi(new JSONObject()
                .put("type", "conversation.item.create")
                .put("item", outputItem));
        sendOpenAi(new JSONObject().put("type", "response.create"));
    }

    /**
     * Returns null only when the carrier actually accepted the transfer. Any
     * non-null value is a tool result that must be sent back to the model.
     */
    private String executeHumanTransfer(String toolResult) {
        try {
            JSONObject parsed = new JSONObject(toolResult);
            if (!parsed.optBoolean("success", false)) return toolResult;
            JSONObject data = parsed.optJSONObject("data");
            String target = data == null ? null : data.optString("targetPhone", null);
            if (target != null && transport.transferToHuman(target)) {
                log.info("Human transfer accepted by carrier call={}", context.callId());
                return null;
            }
        } catch (Exception e) {
            log.warn("Could not execute human transfer for call {}: {}", context.callId(), e.getMessage());
        }
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "HUMAN_TRANSFER_FAILED")
                        .put("message", "No pude completar la transferencia con el proveedor telefónico. Puedes seguir ayudando al cliente por voz."))
                .toString();
    }

    private void forwardAudio(String delta) {
        if (delta == null || delta.isBlank()) return;
        transport.sendAudio(context.streamSid(), delta);
    }

    private void clearPlayback() {
        transport.clearPlayback(context.streamSid());
    }

    /**
     * Serialize all client events sent to the JDK WebSocket. Audio frames arrive
     * from the carrier thread while function outputs arrive from the OpenAI
     * listener thread, so writing without ordering can race under real traffic.
     */
    private void sendOpenAi(JSONObject json) {
        WebSocket socket = openAiSocket;
        if (socket == null || !open.get() || closed.get()) return;

        int pending = pendingOpenAiMessages.incrementAndGet();
        if (pending > MAX_PENDING_OPENAI_MESSAGES) {
            pendingOpenAiMessages.decrementAndGet();
            handleUpstreamFailure("OpenAI outbound message backlog exceeded safe limit");
            return;
        }

        String payload = json.toString();
        synchronized (sendLock) {
            sendChain = sendChain.handle((ignored, previousError) -> (Void) null)
                    .thenCompose(ignored -> {
                        if (!open.get() || closed.get()) {
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        return socket.sendText(payload, true).thenApply(sent -> (Void) null);
                    })
                    .whenComplete((ignored, error) -> {
                        pendingOpenAiMessages.decrementAndGet();
                        if (error != null) {
                            handleUpstreamFailure("OpenAI send failed: " + rootMessage(error));
                        }
                    });
        }
    }

    private void logOpenAiError(JSONObject event) {
        JSONObject error = event.optJSONObject("error");
        String code = error == null ? "unknown" : error.optString("code", "unknown");
        String message = error == null ? "Realtime provider error" : error.optString("message", "Realtime provider error");
        log.warn("OpenAI Realtime error call={} code={} message={}", context.callId(), code, message);
    }

    private void handleUpstreamFailure(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        open.set(false);
        log.warn("Realtime upstream failure call={} reason={}", context.callId(), reason);
        transport.closeOnUpstreamFailure();
        summaries.generate(context.callId());
    }

    private void finishAiSession(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        open.set(false);
        WebSocket socket = openAiSocket;
        if (socket != null) {
            try { socket.sendClose(WebSocket.NORMAL_CLOSURE, reason); }
            catch (Exception ignored) { }
        }
        summaries.generate(context.callId());
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "unknown";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override
    public void close() {
        finishAiSession("Voice transport ended");
    }
}
