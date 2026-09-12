package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OpenAiLiveSidebandManager {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveSidebandManager.class);

    private final OpenAiRealtimeProperties openAi;
    private final OpenAiLiveProperties live;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcripts;
    private final CallSummaryService summaries;
    private final CallLifecycleService lifecycle;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiLiveSidebandManager(OpenAiRealtimeProperties openAi,
                                     OpenAiLiveProperties live,
                                     RealtimeToolService tools,
                                     CallTranscriptService transcripts,
                                     CallSummaryService summaries,
                                     CallLifecycleService lifecycle) {
        this.openAi = openAi;
        this.live = live;
        this.tools = tools;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.lifecycle = lifecycle;
    }

    public void attach(String sessionId, RealtimeCallContext context) {
        String pathId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8).replace("+", "%20");
        String url = live.normalizedSidebandBaseUrl() + "/live/sessions/" + pathId + "/attach";
        SidebandSession listener = new SidebandSession(sessionId, context);
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + openAi.getApiKey())
                .header("OpenAI-Project", live.getProjectId().trim())
                .buildAsync(URI.create(url), listener)
                .exceptionally(error -> {
                    listener.fail("Could not attach GPT-Live sideband: " + rootMessage(error));
                    hangup(sessionId);
                    return null;
                });
    }

    private final class SidebandSession implements WebSocket.Listener {
        private final String sessionId;
        private final RealtimeCallContext context;
        private final Set<String> completedToolCalls = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private final AtomicBoolean sessionClosedEvent = new AtomicBoolean(false);
        private final StringBuilder frameBuffer = new StringBuilder();
        private final StringBuilder userTranscript = new StringBuilder();
        private final StringBuilder assistantTranscript = new StringBuilder();
        private final Object sendLock = new Object();
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile WebSocket socket;

        private SidebandSession(String sessionId, RealtimeCallContext context) {
            this.sessionId = sessionId;
            this.context = context;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            webSocket.request(1);
            send(new JSONObject()
                    .put("type", "session.commentary.append")
                    .put("event_id", eventId())
                    .put("delegation_id", JSONObject.NULL)
                    .put("content", "Saluda ahora al cliente de forma breve y natural, di el nombre del negocio si lo conoces y pregunta en qué puedes ayudar."));
            log.info("GPT-Live sideband attached call={} session={}", context.callId(), sessionId);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (frameBuffer) {
                frameBuffer.append(data);
                if (last) {
                    String payload = frameBuffer.toString();
                    frameBuffer.setLength(0);
                    handle(payload);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            boolean cleanLiveClose = sessionClosedEvent.get();
            finish(!cleanLiveClose,
                    (cleanLiveClose ? "sideband closed after session.closed" : "unexpected sideband close")
                            + " status=" + statusCode + " reason=" + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail("GPT-Live sideband error: " + rootMessage(error));
            hangup(sessionId);
        }

        private void handle(String payload) {
            try {
                JSONObject event = new JSONObject(payload);
                String type = event.optString("type", "");
                switch (type) {
                    case "session.input_transcript.delta" -> append(userTranscript, event.optString("delta", ""));
                    case "session.output_transcript.delta" -> append(assistantTranscript, event.optString("delta", ""));
                    case "session.delegation.created" -> flushTranscripts();
                    case "response.event" -> handleResponseEvent(event.optJSONObject("event"));
                    case "session.closed" -> {
                        sessionClosedEvent.set(true);
                        finish(false, "GPT-Live session closed");
                    }
                    case "error" -> logLiveError(event);
                    default -> { }
                }
            } catch (Exception e) {
                log.warn("Could not process GPT-Live event call={} session={}: {}",
                        context.callId(), sessionId, e.getMessage());
            }
        }

        private void handleResponseEvent(JSONObject responseEvent) {
            if (responseEvent == null || !"response.output_item.done".equals(responseEvent.optString("type"))) return;
            JSONObject item = responseEvent.optJSONObject("item");
            if (item == null || !"function_call".equals(item.optString("type"))) return;

            String callId = item.optString("call_id", null);
            String name = item.optString("name", null);
            String arguments = item.optString("arguments", "{}");
            if (callId == null || name == null || !completedToolCalls.add(callId)) return;

            String result = tools.execute(context, name, arguments);
            if ("transfer_to_human".equals(name)) {
                result = executeHumanTransfer(result);
            }

            send(new JSONObject()
                    .put("type", "response.item.create")
                    .put("event_id", eventId())
                    .put("item", new JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", callId)
                            .put("output", result)));
            send(new JSONObject()
                    .put("type", "response.create")
                    .put("event_id", eventId()));
        }

        private String executeHumanTransfer(String toolResult) {
            try {
                JSONObject parsed = new JSONObject(toolResult);
                if (!parsed.optBoolean("success", false)) return toolResult;
                JSONObject data = parsed.optJSONObject("data");
                String target = data == null ? null : data.optString("targetPhone", null);
                if (target == null || target.isBlank() || !referToHuman(sessionId, target)) {
                    return toolFailure("HUMAN_TRANSFER_FAILED",
                            "No pude completar la transferencia telefónica. Continúa ayudando al cliente por voz.");
                }
                log.info("GPT-Live SIP transfer accepted call={} session={}", context.callId(), sessionId);
                return new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("transferred", true))
                        .put("error", JSONObject.NULL)
                        .toString();
            } catch (Exception e) {
                return toolFailure("HUMAN_TRANSFER_FAILED",
                        "No pude completar la transferencia telefónica. Continúa ayudando al cliente por voz.");
            }
        }

        private void logLiveError(JSONObject event) {
            JSONObject error = event.optJSONObject("error");
            String message = error == null ? event.toString() : error.optString("message", error.toString());
            log.warn("GPT-Live error call={} session={} message={}", context.callId(), sessionId, message);
        }

        private void send(JSONObject event) {
            WebSocket ws = socket;
            if (ws == null || terminal.get()) return;
            String payload = event.toString();
            synchronized (sendLock) {
                sendChain = sendChain.handle((ignored, previousError) -> (Void) null)
                        .thenCompose(ignored -> {
                            if (terminal.get()) return CompletableFuture.completedFuture(null);
                            return ws.sendText(payload, true).thenApply(sent -> (Void) null);
                        })
                        .whenComplete((ignored, error) -> {
                            if (error != null && !terminal.get()) {
                                fail("GPT-Live sideband send failed: " + rootMessage(error));
                                hangup(sessionId);
                            }
                        });
            }
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

        private void finish(boolean failed, String reason) {
            if (!terminal.compareAndSet(false, true)) return;
            flushTranscripts();
            try {
                lifecycle.updateStatus(context.callId(), failed ? "failed" : "completed", null);
            } catch (Exception e) {
                log.warn("Could not finalize GPT-Live call {}: {}", context.callId(), e.getMessage());
            }
            try {
                summaries.generate(context.callId());
            } catch (Exception e) {
                log.warn("Could not summarize GPT-Live call {}: {}", context.callId(), e.getMessage());
            }
            log.info("GPT-Live sideband ended call={} session={} failed={} reason={}",
                    context.callId(), sessionId, failed, reason);
        }

        private void fail(String reason) {
            log.warn("GPT-Live sideband failure call={} session={} reason={}", context.callId(), sessionId, reason);
            finish(true, reason);
        }
    }

    private boolean referToHuman(String sessionId, String targetPhone) {
        JSONObject body = new JSONObject().put("target_uri", "tel:" + targetPhone.trim());
        return postControl(sessionId, "refer", body.toString());
    }

    private void hangup(String sessionId) {
        postControl(sessionId, "hangup", null);
    }

    private boolean postControl(String sessionId, String action, String body) {
        try {
            String pathId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(live.normalizedApiBaseUrl() + "/live/sessions/" + pathId + "/" + action))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + openAi.getApiKey())
                    .header("OpenAI-Project", live.getProjectId().trim());
            if (body == null) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return true;
            log.warn("GPT-Live control action={} session={} status={} body={}",
                    action, sessionId, response.statusCode(), truncate(response.body()));
        } catch (Exception e) {
            log.warn("GPT-Live control action={} session={} failed: {}", action, sessionId, e.getMessage());
        }
        return false;
    }

    private static void append(StringBuilder target, String delta) {
        if (delta == null || delta.isEmpty()) return;
        synchronized (target) {
            target.append(delta);
        }
    }

    private static String toolFailure(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message))
                .toString();
    }

    private static String eventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "unknown";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
