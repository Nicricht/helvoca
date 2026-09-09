package cl.helvoca.ai.realtime;

import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpenAiRealtimeBridge implements WebSocket.Listener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeBridge.class);
    private static final int MAX_QUEUED_AUDIO_FRAMES = 250;

    private final RealtimeCallContext context;
    private final WebSocketSession twilioSession;
    private final OpenAiRealtimeProperties properties;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final HttpClient httpClient;
    private final Queue<String> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final StringBuilder incomingText = new StringBuilder();

    private volatile WebSocket openAiSocket;
    private volatile RealtimeAgentRuntimeConfig runtime;

    OpenAiRealtimeBridge(RealtimeCallContext context,
                         WebSocketSession twilioSession,
                         OpenAiRealtimeProperties properties,
                         RealtimeToolService tools,
                         CallTranscriptService transcripts,
                         CallSummaryService summaries,
                         HttpClient httpClient) {
        this.context = context;
        this.twilioSession = twilioSession;
        this.properties = properties;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.httpClient = httpClient;
    }

    public void start() {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for realtime calls");
        }
        runtime = tools.runtimeConfig(context);
        if (!runtime.active()) {
            throw new IllegalStateException("AI agent is disabled for this business");
        }
        String url = properties.getRealtimeUrl() + "?model="
                + URLEncoder.encode(properties.getRealtimeModel(), StandardCharsets.UTF_8);
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("OpenAI-Safety-Identifier", context.businessId().toString())
                .buildAsync(URI.create(url), this)
                .exceptionally(error -> {
                    log.warn("Could not open OpenAI Realtime for call {}: {}", context.callId(), error.getMessage());
                    closeTwilioOnUpstreamFailure();
                    return null;
                });
    }

    public void acceptTwilioAudio(String base64Pcmu) {
        if (closed.get() || base64Pcmu == null || base64Pcmu.isBlank()) return;
        if (open.get()) {
            sendOpenAi(new JSONObject().put("type", "input_audio_buffer.append").put("audio", base64Pcmu));
            return;
        }
        if (pendingAudio.size() < MAX_QUEUED_AUDIO_FRAMES) pendingAudio.offer(base64Pcmu);
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
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        log.warn("OpenAI Realtime error for call {}: {}", context.callId(), error.getMessage());
    }

    private void sendSessionUpdate() {
        RealtimeAgentRuntimeConfig cfg = requireRuntime();
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

        String voice = cfg.voice() == null || cfg.voice().isBlank() ? properties.getVoice() : cfg.voice();
        JSONObject output = new JSONObject()
                .put("format", new JSONObject().put("type", "audio/pcmu"))
                .put("voice", voice);

        JSONObject session = new JSONObject()
                .put("type", "realtime")
                .put("model", properties.getRealtimeModel())
                .put("instructions", cfg.instructions())
                .put("output_modalities", new JSONArray().put("audio"))
                .put("audio", new JSONObject().put("input", input).put("output", output))
                .put("tools", RealtimeToolDefinitions.enabled(cfg.capabilities()))
                .put("tool_choice", "auto");

        sendOpenAi(new JSONObject().put("type", "session.update").put("session", session));
    }

    private void sendGreeting() {
        RealtimeAgentRuntimeConfig cfg = requireRuntime();
        String greeting = cfg.greeting() == null || cfg.greeting().isBlank()
                ? "Hola. ¿En qué puedo ayudarte?"
                : cfg.greeting();
        JSONObject response = new JSONObject()
                .put("instructions", "Pronuncia el siguiente saludo de apertura de forma natural, sin agregar acciones ni resultados: " + greeting)
                .put("output_modalities", new JSONArray().put("audio"));
        sendOpenAi(new JSONObject().put("type", "response.create").put("response", response));
    }

    private RealtimeAgentRuntimeConfig requireRuntime() {
        RealtimeAgentRuntimeConfig value = runtime;
        if (value == null) throw new IllegalStateException("Realtime agent configuration not loaded");
        return value;
    }

    private void handleOpenAiEvent(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String type = event.optString("type", "");
            switch (type) {
                case "response.output_audio.delta" -> forwardAudio(event.optString("delta", null));
                case "input_audio_buffer.speech_started" -> clearTwilioAudio();
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
        JSONObject outputItem = new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", result);
        sendOpenAi(new JSONObject()
                .put("type", "conversation.item.create")
                .put("item", outputItem));
        sendOpenAi(new JSONObject().put("type", "response.create"));
    }

    private void forwardAudio(String delta) {
        if (delta == null || delta.isBlank()) return;
        JSONObject message = new JSONObject()
                .put("event", "media")
                .put("streamSid", context.streamSid())
                .put("media", new JSONObject().put("payload", delta));
        sendTwilio(message);
    }

    private void clearTwilioAudio() {
        sendTwilio(new JSONObject().put("event", "clear").put("streamSid", context.streamSid()));
    }

    private void sendTwilio(JSONObject json) {
        try {
            synchronized (twilioSession) {
                if (twilioSession.isOpen()) twilioSession.sendMessage(new TextMessage(json.toString()));
            }
        } catch (Exception e) {
            log.warn("Could not send audio to Twilio for call {}: {}", context.callId(), e.getMessage());
        }
    }

    private void sendOpenAi(JSONObject json) {
        WebSocket socket = openAiSocket;
        if (socket != null && open.get() && !closed.get()) {
            socket.sendText(json.toString(), true);
        }
    }

    private void logOpenAiError(JSONObject event) {
        JSONObject error = event.optJSONObject("error");
        String code = error == null ? "unknown" : error.optString("code", "unknown");
        String message = error == null ? "Realtime provider error" : error.optString("message", "Realtime provider error");
        log.warn("OpenAI Realtime error call={} code={} message={}", context.callId(), code, message);
    }

    private void closeTwilioOnUpstreamFailure() {
        try {
            if (twilioSession.isOpen()) {
                twilioSession.close(org.springframework.web.socket.CloseStatus.SERVER_ERROR);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        open.set(false);
        WebSocket socket = openAiSocket;
        if (socket != null) {
            try { socket.sendClose(WebSocket.NORMAL_CLOSURE, "Twilio stream ended"); }
            catch (Exception ignored) { }
        }
        summaries.generate(context.callId());
    }
}
