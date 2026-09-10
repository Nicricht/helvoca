package cl.helvoca.call;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class CallSummaryService {
    private static final Logger log = LoggerFactory.getLogger(CallSummaryService.class);
    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;
    private final OpenAiRealtimeProperties openAi;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CallSummaryService(CallTranscriptRepository transcripts,
                              CallSummaryRepository summaries,
                              OpenAiRealtimeProperties openAi) {
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.openAi = openAi;
    }

    @Async
    public void generate(UUID callId) {
        try {
            Thread.sleep(500);
            if (summaries.findByCallId(callId).isPresent()) return;
            List<CallTranscript> items = transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId);
            if (items.isEmpty()) return;

            String transcript = buildTranscript(items);
            if (!openAi.hasApiKey()) {
                save(callId, "La llamada registró " + items.size() + " intervenciones. No se generó resumen semántico porque OPENAI_API_KEY no está configurada.");
                return;
            }

            JSONObject body = new JSONObject()
                    .put("model", openAi.getSummaryModel())
                    .put("instructions", "Resume esta llamada empresarial en español. Sé factual. Incluye motivo, datos importantes, acciones realmente confirmadas y pendientes. Nunca inventes una reserva, pago, pedido o resultado que no aparezca confirmado en la transcripción.")
                    .put("input", transcript)
                    .put("max_output_tokens", 350);

            HttpRequest request = HttpRequest.newBuilder(URI.create(openAi.getResponsesUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openAi.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("OpenAI summary failed for call {} with HTTP {}", callId, response.statusCode());
                save(callId, "La llamada registró " + items.size() + " intervenciones. El proveedor de IA no pudo generar el resumen automático.");
                return;
            }

            String text = extractOutputText(new JSONObject(response.body()));
            if (text == null || text.isBlank()) {
                save(callId, "La llamada registró " + items.size() + " intervenciones. El proveedor no devolvió texto de resumen.");
            } else {
                save(callId, text.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not generate summary for call {}: {}", callId, e.getMessage());
        }
    }

    private void save(UUID callId, String text) {
        if (summaries.findByCallId(callId).isPresent()) return;
        CallSummary summary = new CallSummary();
        summary.setCallId(callId);
        summary.setSummary(text);
        summaries.save(summary);
        log.info("Call summary persisted for call {}", callId);
    }

    private static String buildTranscript(List<CallTranscript> items) {
        StringBuilder out = new StringBuilder();
        for (CallTranscript item : items) {
            if (out.length() > 12_000) break;
            out.append(item.getSpeaker()).append(": ").append(item.getContent()).append('\n');
        }
        return out.toString();
    }

    static String extractOutputText(JSONObject root) {
        String direct = root.optString("output_text", "");
        if (!direct.isBlank()) return direct;
        JSONArray output = root.optJSONArray("output");
        if (output == null) return null;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    String text = part.optString("text", "");
                    if (!text.isBlank()) return text;
                }
            }
        }
        return null;
    }
}
