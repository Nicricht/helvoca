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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Optional one-shot production diagnostic for Gemini Live. It validates the
 * real API key, WebSocket endpoint and model without involving Twilio.
 *
 * Gemini Live returns server JSON as binary UTF-8 WebSocket frames in common
 * runtimes, including setupComplete. The probe therefore consumes both text
 * and binary frames. It uses the API-reference setup shape with
 * generationConfig.responseModalities and keeps all product behavior out of
 * this connectivity check.
 *
 * Probe failures are logged but never crash the application. Enable only for
 * a deliberate probe deployment with GEMINI_LIVE_PROBE_ON_STARTUP=true, then
 * disable it again.
 */
@Component
public class GeminiLiveStartupProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(GeminiLiveStartupProbe.class);
    private static final int RESULT_TIMEOUT_SECONDS = 20;

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
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!properties.ready()) {
            log.error("GEMINI_LIVE_PROBE FAILED reason=Gemini Live is not configured");
            return;
        }

        long started = System.nanoTime();
        ProbeListener listener = new ProbeListener(properties.getModel());
        String separator = properties.getWebsocketUrl().contains("?") ? "&" : "?";
        String url = properties.getWebsocketUrl().trim()
                + separator + "key="
                + URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);

        try {
            http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .buildAsync(URI.create(url), listener)
                    .get(10, TimeUnit.SECONDS);

            ProbeResult result = listener.result().get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            if (result.success()) {
                log.info("GEMINI_LIVE_PROBE SUCCESS model={} elapsed_ms={} result={}",
                        properties.getModel(), elapsedMs, result.detail());
            } else {
                log.error("GEMINI_LIVE_PROBE FAILED model={} elapsed_ms={} reason={}",
                        properties.getModel(), elapsedMs, result.detail());
            }
        } catch (Exception e) {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.error("GEMINI_LIVE_PROBE FAILED model={} elapsed_ms={} reason={}",
                    properties.getModel(), elapsedMs, rootMessage(e));
        }
    }

    private static final class ProbeListener implements WebSocket.Listener {
        private final String model;
        private final CompletableFuture<ProbeResult> result = new CompletableFuture<>();
        private final GeminiWebSocketJsonFrames inboundFrames = new GeminiWebSocketJsonFrames();
        private volatile WebSocket socket;

        private ProbeListener(String model) {
            this.model = model;
        }

        CompletableFuture<ProbeResult> result() {
            return result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            webSocket.request(1);
            log.info("GEMINI_LIVE_PROBE socket connected; sending generationConfig-only setup model={}", model);
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
            try {
                String payload = inboundFrames.acceptText(data, last);
                if (payload != null) {
                    log.info("GEMINI_LIVE_PROBE text-frame={}", truncate(payload));
                    handle(payload);
                }
            } catch (Exception e) {
                result.complete(new ProbeResult(false, "invalid text frame: " + e.getMessage()));
            } finally {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            try {
                String payload = inboundFrames.acceptBinary(data, last);
                if (payload != null) {
                    log.info("GEMINI_LIVE_PROBE binary-frame={}", truncate(payload));
                    handle(payload);
                }
            } catch (Exception e) {
                result.complete(new ProbeResult(false, "invalid binary frame: " + e.getMessage()));
            } finally {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            inboundFrames.reset();
            log.warn("GEMINI_LIVE_PROBE socket closed status={} reason={}", statusCode, reason);
            result.complete(new ProbeResult(false,
                    "socket closed before setupComplete status=" + statusCode + " reason=" + reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            inboundFrames.reset();
            log.warn("GEMINI_LIVE_PROBE websocket error={}", rootMessage(error));
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
            JSONObject generationConfig = new JSONObject()
                    .put("responseModalities", new JSONArray().put("AUDIO"));
            return new JSONObject().put("setup", new JSONObject()
                    .put("model", "models/" + model.trim())
                    .put("generationConfig", generationConfig));
        }
    }

    private record ProbeResult(boolean success, String detail) {
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
