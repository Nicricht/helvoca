package cl.helvoca.telephony.twilio.trial;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.call.CallTranscriptService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TrialVoiceConversationService {
    private static final Logger log = LoggerFactory.getLogger(TrialVoiceConversationService.class);
    private static final Duration FIRST_REQUEST_TIMEOUT = Duration.ofMillis(2200);
    private static final Duration SECOND_REQUEST_TIMEOUT = Duration.ofMillis(1500);

    private final TrialVoiceProperties trial;
    private final OpenAiRealtimeProperties openAi;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcriptWriter;
    private final CallTranscriptRepository transcriptRepository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public TrialVoiceConversationService(TrialVoiceProperties trial,
                                         OpenAiRealtimeProperties openAi,
                                         RealtimeToolService tools,
                                         CallTranscriptService transcriptWriter,
                                         CallTranscriptRepository transcriptRepository) {
        this.trial = trial;
        this.openAi = openAi;
        this.tools = tools;
        this.transcriptWriter = transcriptWriter;
        this.transcriptRepository = transcriptRepository;
    }

    public TrialVoiceReply reply(RealtimeCallContext context, String speech) {
        if (speech == null || speech.isBlank()) {
            return new TrialVoiceReply("No alcancé a escucharte. Inténtalo nuevamente.", false);
        }

        String cleanSpeech = speech.trim();
        transcriptWriter.append(context.callId(), "USER", cleanSpeech);

        if (isFarewell(cleanSpeech)) {
            String goodbye = "Gracias por llamar a Helvoca. Hasta luego.";
            transcriptWriter.append(context.callId(), "ASSISTANT", goodbye);
            return new TrialVoiceReply(goodbye, true);
        }

        int userTurns = countUserTurns(context);
        if (userTurns >= Math.max(1, trial.getMaxTurns())) {
            String limit = "La demostración llegó a su límite de turnos. Gracias por probar Helvoca.";
            transcriptWriter.append(context.callId(), "ASSISTANT", limit);
            return new TrialVoiceReply(limit, true);
        }

        if (!openAi.hasApiKey()) {
            String unavailable = "La inteligencia artificial no está configurada en este momento.";
            transcriptWriter.append(context.callId(), "ASSISTANT", unavailable);
            return new TrialVoiceReply(unavailable, false);
        }

        try {
            String instructions = tools.buildInstructions(context) + "\n" + """
                    Estás atendiendo mediante el modo de prueba telefónica de Twilio basado en turnos.
                    Tu respuesta será leída en voz alta por teléfono. Responde en español, sin markdown y con un máximo de 35 palabras.
                    Si necesitas datos oficiales o ejecutar una acción, usa las herramientas disponibles.
                    No afirmes que una acción fue exitosa si la herramienta no devolvió success=true.
                    Hora actual UTC: %s.
                    """.formatted(Instant.now());

            JSONObject firstBody = baseRequest(instructions)
                    .put("input", buildHistory(context));
            JSONObject first = send(firstBody, FIRST_REQUEST_TIMEOUT);

            String text = extractOutputText(first);
            JSONArray functionCalls = functionCalls(first);
            if (functionCalls.isEmpty() && text != null && !text.isBlank()) {
                return persist(context, text, false);
            }

            if (!functionCalls.isEmpty()) {
                List<ToolExecution> executions = executeTools(context, functionCalls);
                JSONArray outputs = new JSONArray();
                for (ToolExecution execution : executions) {
                    outputs.put(new JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", execution.callId())
                            .put("output", execution.result()));
                }

                String responseId = first.optString("id", "");
                if (!responseId.isBlank()) {
                    JSONObject secondBody = baseRequest(instructions)
                            .put("previous_response_id", responseId)
                            .put("input", outputs);
                    try {
                        JSONObject second = send(secondBody, SECOND_REQUEST_TIMEOUT);
                        String secondText = extractOutputText(second);
                        if (secondText != null && !secondText.isBlank()) {
                            return persist(context, secondText, false);
                        }
                    } catch (Exception secondFailure) {
                        log.info("Trial voice second AI turn fell back to backend result for call {}: {}",
                                context.callId(), secondFailure.getMessage());
                    }
                }

                return persist(context, summarizeToolResults(executions), false);
            }

            return persist(context, "No pude generar una respuesta clara. ¿Puedes repetirlo de otra forma?", false);
        } catch (Exception e) {
            log.warn("Trial voice AI response failed for call {}: {}", context.callId(), e.getMessage());
            return persist(context, "Estoy teniendo una demora con la inteligencia artificial. Inténtalo nuevamente.", false);
        }
    }

    private JSONObject baseRequest(String instructions) {
        return new JSONObject()
                .put("model", openAi.getTrialModel())
                .put("instructions", instructions)
                .put("tools", responseTools())
                .put("tool_choice", "auto")
                .put("max_output_tokens", 120);
    }

    private JSONObject send(JSONObject body, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(openAi.getResponsesUrl()))
                .timeout(timeout)
                .header("Authorization", "Bearer " + openAi.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenAI HTTP " + response.statusCode());
        }
        return new JSONObject(response.body());
    }

    private JSONArray responseTools() {
        JSONArray realtime = RealtimeToolDefinitions.all();
        JSONArray out = new JSONArray();
        for (int i = 0; i < realtime.length(); i++) {
            JSONObject copy = new JSONObject(realtime.getJSONObject(i).toString());
            copy.put("strict", false);
            out.put(copy);
        }
        return out;
    }

    private List<ToolExecution> executeTools(RealtimeCallContext context, JSONArray calls) {
        List<ToolExecution> out = new ArrayList<>();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i);
            String callId = call.optString("call_id", "");
            String name = call.optString("name", "");
            String arguments = call.optString("arguments", "{}");
            if (callId.isBlank() || name.isBlank()) continue;
            out.add(new ToolExecution(callId, name, tools.execute(context, name, arguments)));
        }
        return out;
    }

    private JSONArray functionCalls(JSONObject root) {
        JSONArray output = root.optJSONArray("output");
        JSONArray calls = new JSONArray();
        if (output == null) return calls;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null && "function_call".equals(item.optString("type"))) {
                calls.put(item);
            }
        }
        return calls;
    }

    private String extractOutputText(JSONObject root) {
        String direct = root.optString("output_text", "");
        if (!direct.isBlank()) return direct.trim();
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
                    if (!text.isBlank()) return text.trim();
                }
            }
        }
        return null;
    }

    private String buildHistory(RealtimeCallContext context) {
        List<CallTranscript> items = transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(context.callId());
        int start = Math.max(0, items.size() - 12);
        StringBuilder out = new StringBuilder("Conversación telefónica hasta ahora:\n");
        for (int i = start; i < items.size(); i++) {
            CallTranscript item = items.get(i);
            out.append(item.getSpeaker()).append(": ").append(item.getContent()).append('\n');
            if (out.length() > 6_000) break;
        }
        out.append("Responde a la última intervención del cliente.");
        return out.toString();
    }

    private int countUserTurns(RealtimeCallContext context) {
        int count = 0;
        for (CallTranscript item : transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(context.callId())) {
            if ("USER".equalsIgnoreCase(item.getSpeaker())) count++;
        }
        return count;
    }

    private TrialVoiceReply persist(RealtimeCallContext context, String text, boolean endCall) {
        String safe = sanitizeForSpeech(text);
        transcriptWriter.append(context.callId(), "ASSISTANT", safe);
        return new TrialVoiceReply(safe, endCall);
    }

    private String summarizeToolResults(List<ToolExecution> executions) {
        if (executions.isEmpty()) return "No pude completar la operación solicitada.";
        ToolExecution execution = executions.get(executions.size() - 1);
        JSONObject result;
        try {
            result = new JSONObject(execution.result());
        } catch (Exception e) {
            return "La operación fue procesada, pero no pude interpretar el resultado.";
        }

        if (!result.optBoolean("success", false)) {
            JSONObject error = result.optJSONObject("error");
            return error == null ? "La operación no pudo completarse." : error.optString("message", "La operación no pudo completarse.");
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) return "La operación se completó correctamente.";
        return switch (execution.name()) {
            case "create_booking" -> "Perfecto, la reserva quedó confirmada para " + data.optString("startAt", "el horario solicitado") + ".";
            case "check_booking_availability" -> data.optBoolean("available", false)
                    ? "Sí, el horario solicitado está disponible."
                    : "Ese horario no está disponible. Puedo ayudarte a buscar otra alternativa.";
            case "register_caller" -> "Perfecto, tus datos quedaron registrados.";
            case "find_caller" -> data.optBoolean("found", false)
                    ? "Ya encontré tu registro de cliente."
                    : "Todavía no encuentro un cliente asociado a este teléfono.";
            case "list_services" -> serviceNames(data);
            case "get_business_information" -> "Estás llamando a " + data.optString("name", "este negocio") + ".";
            case "search_knowledge" -> firstKnowledge(data);
            default -> "La operación se completó correctamente.";
        };
    }

    private String serviceNames(JSONObject data) {
        JSONArray services = data.optJSONArray("services");
        if (services == null || services.isEmpty()) return "No hay servicios activos configurados.";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(services.length(), 4); i++) {
            names.add(services.getJSONObject(i).optString("name", "servicio"));
        }
        return "Los servicios disponibles son " + String.join(", ", names) + ".";
    }

    private String firstKnowledge(JSONObject data) {
        JSONArray results = data.optJSONArray("results");
        if (results == null || results.isEmpty()) return "No encontré información oficial sobre eso.";
        String content = results.getJSONObject(0).optString("content", "");
        return content.isBlank() ? "Encontré información, pero está vacía." : content;
    }

    private String sanitizeForSpeech(String text) {
        if (text == null || text.isBlank()) return "No pude responder en este momento.";
        String cleaned = text.replaceAll("[`*_#>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    private boolean isFarewell(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("adiós") || normalized.contains("adios")
                || normalized.contains("hasta luego") || normalized.contains("chao")
                || normalized.contains("chau") || normalized.contains("eso es todo");
    }

    private record ToolExecution(String callId, String name, String result) {}
}
