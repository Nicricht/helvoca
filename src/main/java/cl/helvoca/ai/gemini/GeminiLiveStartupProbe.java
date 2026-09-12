package cl.helvoca.ai.gemini;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Optional one-shot production diagnostic for Gemini Live. It validates the
 * real API key, WebSocket endpoint, model and voice without involving Twilio.
 * Enable only for a deliberate probe deployment with
 * GEMINI_LIVE_PROBE_ON_STARTUP=true, then disable it again.
 */
@Component
public class GeminiLiveStartupProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(GeminiLiveStartupProbe.class);

    private final GeminiLiveProperties properties;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public GeminiLiveStartupProbe(GeminiLiveProperties properties,
                                  @Value("${GEMINI_LIVE_PROBE_ON_STARTUP:false}") boolean enabled) {
        this.properties = properties;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        if (!properties.ready()) {
            throw new IllegalStateException("Gemini Live startup probe requested but Gemini Live is not configured");
        }

        long started = System.nanoTime();
        ProbeListener listener = new ProbeListener(properties.getModel(), properties.getVoice());
        String separator = properties.getWebsocketUrl().contains("?") ? "&" : "?";
        String url = properties.getWebsocketUrl().trim()
                + separator + "key="
                + URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);

        try {
            http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .buildAsync(URI.create(url), listener)
                    .get(10, TimeUnit.SECONDS);

            ProbeResult result = listener.result().get(12, TimeUnit.SECONDS);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (!result.success()) {
                throw new IllegalStateException("Gemini Live startup probe failed: " + result.detail());
            }
            log.info("GEMINI_LIVE_PROBE SUCCESS model={} voice={} elapsed_ms={}",
                    properties.getModel(), properties.getVoice(), elapsedMs);
        } catch (Exception e) {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.error("GEMINI_LIVE_PROBE FAILED model={} voice={} elapsed_ms={} reason={}",
                    properties.getModel(), properties.getVoice(), elapsedMs, rootMessage(e));
            throw e;
        }
    }

    private static final class ProbeListener implements WebSocket.Listener {
        private final String model;
        private final String voice;
        private final CompletableFuture<ProbeResult> result = new CompletableFuture<>();
        private final StringBuilder frameBuffer = new StringBuilder();
        private volatile WebSocket socket;

        private ProbeListener(String model, String voice) {
            this.model = model;
            this.voice = voice;
        }

        CompletableFuture<ProbeResult> result() {
            return result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            webSocket.request(1);
            webSocket.sendText(setup().toString(), true)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            result.complete(new ProbeResult(false,
                                    "setup send failed: " + rootMessage(error)));
                        }
                    });
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
            result.complete(new ProbeResult(false,
                    "socket closed before setupComplete status=" + statusCode + " reason=" + reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            result.complete(new ProbeResult(false, "websocket error: " + rootMessage(error)));
        }

        private void handle(String payload) {
            try {
                JSONObject event = new JSONObject(payload);
                if (event.has("setupComplete")) {
                    result.complete(new ProbeResult(true, "setupComplete"));
                    WebSocket current = socket;
                    if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "probe complete");
                    return;
                }
                JSONObject error = event.optJSONObject("error");
                if (error != null) {
                    int code = error.optInt("code", 0);
                    String status = error.optString("status", "");
                    String message = error.optString("message", "Gemini Live error");
                    result.complete(new ProbeResult(false,
                            "provider error code=" + code + " status=" + status + " message=" + message));
                }
            } catch (Exception e) {
                result.complete(new ProbeResult(false, "invalid provider payload: " + e.getMessage()));
            }
        }

        private JSONObject setup() {
            JSONObject generation = new JSONObject()
                    .put("responseModalities", new JSONArray().put("AUDIO"))
                    .put("speechConfig", new JSONObject()
                            .put("voiceConfig", new JSONObject()
                                    .put("prebuiltVoiceConfig", new JSONObject()
                                            .put("voiceName", voice))));
            return new JSONObject().put("setup", new JSONObject()
                    .put("model", "models/" + model.trim())
                    .put("generationConfig", generation));
        }
    }

    private record ProbeResult(boolean success, String detail) {
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
