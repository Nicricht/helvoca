package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Temporary, read-only diagnostic used to verify whether the connected OpenAI
 * project API key can access the dashboard webhook-endpoint surface. It never
 * creates, updates, rotates or deletes a webhook and never logs credentials.
 */
@Component
public class OpenAiWebhookEndpointProbe {
    private static final Logger log = LoggerFactory.getLogger(OpenAiWebhookEndpointProbe.class);
    private static final String ENDPOINT = "https://api.openai.com/v1/dashboard/webhook_endpoints";

    private final OpenAiRealtimeProperties openAi;
    private final OpenAiLiveProperties live;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public OpenAiWebhookEndpointProbe(OpenAiRealtimeProperties openAi,
                                      OpenAiLiveProperties live) {
        this.openAi = openAi;
        this.live = live;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void probe() {
        if (!enabled()) return;
        if (!openAi.hasApiKey() || !live.hasProjectId()) {
            log.warn("OpenAI webhook probe skipped: API key or project id is missing");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + openAi.getApiKey())
                    .header("OpenAI-Project", live.getProjectId().trim())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("OPENAI_WEBHOOK_PROBE status={} body={}",
                    response.statusCode(), sanitize(response.body()));
        } catch (Exception e) {
            log.warn("OPENAI_WEBHOOK_PROBE failed: {}", e.toString());
        }
    }

    private static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("OPENAI_WEBHOOK_PROBE"));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() > 700) compact = compact.substring(0, 700);
        // The probe is GET-only, so no signing_secret should be returned. Still
        // redact common secret-shaped fields defensively before writing logs.
        compact = compact.replaceAll("(?i)(signing_secret|api_key|secret)\\\"?\\s*[:=]\\s*\\\"[^\\\"]+\\\"", "$1:<redacted>");
        return compact;
    }
}
