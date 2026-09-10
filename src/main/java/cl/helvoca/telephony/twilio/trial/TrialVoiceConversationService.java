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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TrialVoiceConversationService {
    private static final Logger log = LoggerFactory.getLogger(TrialVoiceConversationService.class);
    private static final Duration FIRST_REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration SECOND_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final TrialVoiceProperties trial;
    private final OpenAiRealtimeProperties openAi;
    private final RealtimeToolService tools;
    private final CallTranscriptService transcriptWriter;
    private final CallTranscriptRepository transcriptRepository;
    private final TrialConversationStateService state;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public TrialVoiceConversationService(TrialVoiceProperties trial,
                                         OpenAiRealtimeProperties openAi,
                                         RealtimeToolService tools,
                                         CallTranscriptService transcriptWriter,
                                         CallTranscriptRepository transcriptRepository,
                                         TrialConversationStateService state) {
        this.trial = trial;
        this.openAi = openAi;
        this.tools = tools;
        this.transcriptWriter = transcriptWriter;
        this.transcriptRepository = transcriptRepository;
        this.state = state;
    }

    public TrialVoiceReply reply(RealtimeCallContext context, String speech) {
        initializeState(context);

        if (speech == null || speech.isBlank()) {
            return new TrialVoiceReply("No alcancé a escucharte bien. ¿Puedes repetirlo?", false);
        }

        String cleanSpeech = speech.trim();
        transcriptWriter.append(context.callId(), "USER", cleanSpeech);

        if (isFarewell(cleanSpeech)) {
            String goodbye = "Gracias por llamar a Helvoca. Que tengas un excelente día.";
            transcriptWriter.append(context.callId(), "ASSISTANT", goodbye);
            state.clear(context.callId());
            return new TrialVoiceReply(goodbye, true);
        }

        int userTurns = countUserTurns(context);
        if (userTurns > Math.max(1, trial.getMaxTurns())) {
            String limit = "Llegamos al final de esta demostración. Gracias por probar Helvoca.";
            transcriptWriter.append(context.callId(), "ASSISTANT", limit);
            state.clear(context.callId());
            return new TrialVoiceReply(limit, true);
        }

        if (!openAi.hasApiKey()) {
            String unavailable = "En este momento no puedo completar la consulta. ¿Puedes intentarlo nuevamente en unos minutos?";
            transcriptWriter.append(context.callId(), "ASSISTANT", unavailable);
            return new TrialVoiceReply(unavailable, false);
        }

        try {
            String instructions = tools.buildInstructions(context)
                    + state.promptContext(context.callId())
                    + "\n" + """
                    Estás atendiendo mediante el modo de prueba telefónica de Twilio basado en turnos.
                    Tu respuesta será leída en voz alta. Responde en español natural, sin markdown y con un máximo de 35 palabras.
                    Habla como una recepcionista: no describas procesos internos, búsquedas, herramientas, identificadores ni estados técnicos.
                    Si el estado indica que no hay cliente asociado y quiere reservar, pregunta simplemente: ¿A nombre de quién sería?
                    Si ya conoces el único servicio disponible, no vuelvas a listar servicios salvo que el cliente lo pregunte.
                    Si pregunta qué horarios hay en un día, usa list_available_slots y ofrece horas concretas.
                    Si indica una hora concreta, comprueba esa hora antes de decir que está disponible.
                    Si pregunta por sus reservas, usa list_customer_bookings.
                    Si quiere cambiar una reserva y el estado ya contiene bookingId seleccionado, conserva ese bookingId mientras buscas o confirmas la nueva hora y luego usa reschedule_booking.
                    Si quiere cancelar y el estado ya contiene bookingId seleccionado, usa ese bookingId después de confirmar la intención de cancelar.
                    Si hay más de una reserva futura y no hay bookingId seleccionado, pregunta cuál desea cambiar o cancelar antes de ejecutar la acción.
                    No digas que una reserva está disponible, confirmada, reprogramada o cancelada sin haberlo comprobado con una herramienta.
                    Conserva servicio, nombre, bookingId, fecha y hora entre turnos y no vuelvas a preguntarlos si ya aparecen en el estado o historial.
                    Cuando la consulta sea ajena al negocio, redirige brevemente a información, servicios o reservas del negocio.
                    """;

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

                String mutationReply = immediateMutationReply(context, executions);
                if (mutationReply != null) {
                    log.info("Trial voice returned immediate backend mutation confirmation for call {}", context.callId());
                    return persist(context, mutationReply, false);
                }

                JSONArray outputs = new JSONArray();
                for (ToolExecution execution : executions) {
                    outputs.put(new JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", execution.callId())
                            .put("output", execution.result()));
                }

                String responseId = first.optString("id", "");
                if (!responseId.isBlank()) {
                    JSONObject secondBody = baseRequest(instructions + state.promptContext(context.callId()))
                            .put("previous_response_id", responseId)
                            .put("input", outputs);
                    try {
                        JSONObject second = send(secondBody, SECOND_REQUEST_TIMEOUT);
                        String secondText = extractOutputText(second);
                        if (secondText != null && !secondText.isBlank()) {
                            return persist(context, secondText, false);
                        }
                    } catch (Exception secondFailure) {
                        log.info("Trial voice second AI turn used deterministic fallback for call {}: {}",
                                context.callId(), secondFailure.getMessage());
                    }
                }

                return persist(context, summarizeToolResults(context, executions), false);
            }

            return persist(context, "No entendí del todo. ¿Puedes decírmelo de otra forma?", false);
        } catch (Exception e) {
            log.warn("Trial voice AI response failed for call {}: {}", context.callId(), e.getMessage());
            return persist(context, state.timeoutFallback(context.callId(), cleanSpeech), false);
        }
    }

    private void initializeState(RealtimeCallContext context) {
        if (!state.beginInitialization(context.callId())) return;
        String caller = tools.execute(context, "find_caller", "{}");
        state.observeToolResult(context.callId(), "find_caller", caller);
        String services = tools.execute(context, "list_services", "{}");
        state.observeToolResult(context.callId(), "list_services", services);
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
            String result = tools.execute(context, name, arguments);
            state.observeToolResult(context.callId(), name, result);
            out.add(new ToolExecution(callId, name, result));
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

    private String immediateMutationReply(RealtimeCallContext context, List<ToolExecution> executions) {
        for (int i = executions.size() - 1; i >= 0; i--) {
            ToolExecution execution = executions.get(i);
            if (shouldAcknowledgeMutationImmediately(execution.name(), execution.result())) {
                return summarizeToolResults(context, List.of(execution));
            }
        }
        return null;
    }

    static boolean shouldAcknowledgeMutationImmediately(String toolName, String rawResult) {
        if (!("create_booking".equals(toolName)
                || "reschedule_booking".equals(toolName)
                || "cancel_booking".equals(toolName))) {
            return false;
        }
        try {
            return new JSONObject(rawResult).optBoolean("success", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String summarizeToolResults(RealtimeCallContext context, List<ToolExecution> executions) {
        if (executions.isEmpty()) return "No pude completar eso. ¿Quieres que lo intentemos nuevamente?";
        ToolExecution execution = preferredExecution(executions);
        JSONObject result;
        try {
            result = new JSONObject(execution.result());
        } catch (Exception e) {
            return "No pude completar eso. ¿Quieres que lo intentemos nuevamente?";
        }

        if (!result.optBoolean("success", false)) {
            JSONObject error = result.optJSONObject("error");
            String code = error == null ? "" : error.optString("code", "");
            return switch (code) {
                case "CUSTOMER_NOT_REGISTERED" -> "Claro. ¿A nombre de quién sería?";
                case "BOOKING_SLOT_UNAVAILABLE" -> "Ese horario ya no está disponible. ¿Quieres que busque otra hora?";
                case "BUSINESS_CLOSED" -> "Ese horario está fuera del horario de atención. ¿Quieres que busque otra hora?";
                case "BOOKING_NOT_FOUND" -> "No encontré esa reserva entre tus reservas futuras. ¿Quieres que las revise nuevamente?";
                case "BOOKING_CANCELLED" -> "Esa reserva ya está cancelada. ¿Quieres consultar tus otras reservas?";
                case "INVALID_ARGUMENT" -> "Necesito un poco más de información para ayudarte. ¿Puedes repetir la fecha y hora?";
                default -> "No pude completar eso ahora. ¿Quieres que lo intentemos nuevamente?";
            };
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) return "Listo.";
        return switch (execution.name()) {
            case "create_booking" -> "Perfecto. Tu reserva quedó confirmada para "
                    + friendlyLocalDateTime(data.optString("localStart", data.optString("startAt", ""))) + ".";
            case "reschedule_booking" -> "Perfecto. Tu reserva quedó reprogramada para "
                    + friendlyLocalDateTime(data.optString("localStart", data.optString("startAt", ""))) + ".";
            case "cancel_booking" -> "Perfecto. Tu reserva quedó cancelada.";
            case "list_customer_bookings" -> customerBookingsSummary(data);
            case "check_booking_availability" -> {
                if (!data.optBoolean("withinBusinessHours", true)) {
                    yield "Ese horario está fuera del horario de atención. ¿Quieres que busque otra hora?";
                }
                yield data.optBoolean("available", false)
                        ? "Sí, ese horario está disponible. ¿Quieres que confirme la reserva?"
                        : "Ese horario no está disponible. ¿Quieres que busque otra alternativa?";
            }
            case "list_available_slots" -> availableSlotSummary(data);
            case "register_caller" -> state.afterRegistrationPrompt(context.callId());
            case "find_caller" -> data.optBoolean("found", false)
                    ? "Perfecto, ya tengo tus datos."
                    : "Claro. ¿A nombre de quién sería?";
            case "list_services" -> serviceNames(data);
            case "get_business_information" -> "Estás llamando a " + data.optString("name", "este negocio") + ".";
            case "search_knowledge" -> firstKnowledge(data);
            default -> "Listo.";
        };
    }

    private ToolExecution preferredExecution(List<ToolExecution> executions) {
        for (int i = executions.size() - 1; i >= 0; i--) {
            ToolExecution candidate = executions.get(i);
            try {
                if (new JSONObject(candidate.result()).optBoolean("success", false)) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return executions.get(executions.size() - 1);
    }

    private String customerBookingsSummary(JSONObject data) {
        JSONArray bookings = data.optJSONArray("bookings");
        if (bookings == null || bookings.isEmpty()) {
            return "No tienes reservas futuras confirmadas.";
        }
        if (bookings.length() == 1) {
            JSONObject booking = bookings.getJSONObject(0);
            return "Tienes una reserva de " + booking.optString("service", "servicio") + " para "
                    + friendlyLocalDateTime(booking.optString("localStart", booking.optString("startAt", ""))) + ".";
        }
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(bookings.length(), 3); i++) {
            JSONObject booking = bookings.getJSONObject(i);
            items.add(booking.optString("service", "servicio") + " el "
                    + friendlyLocalDateTime(booking.optString("localStart", booking.optString("startAt", ""))));
        }
        return "Tienes " + bookings.length() + " reservas futuras. Las primeras son " + joinSpanish(items)
                + ". ¿Cuál quieres gestionar?";
    }

    private String availableSlotSummary(JSONObject data) {
        if (!data.optBoolean("scheduleConfigured", false)) {
            return "Aún no tengo horarios de atención configurados para ofrecerte horas disponibles.";
        }
        JSONArray slots = data.optJSONArray("slots");
        if (slots == null || slots.isEmpty()) {
            return "No tengo horas disponibles ese día. ¿Quieres consultar otra fecha?";
        }

        List<String> times = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.length(), 5); i++) {
            String raw = slots.getJSONObject(i).optString("localTime", "");
            if (!raw.isBlank()) times.add(spokenTime(raw));
        }
        String day = friendlyDay(data.optString("date", ""), data.optString("timezone", "America/Santiago"));
        return "Para " + day + " tengo disponibilidad a las " + joinSpanish(times) + ". ¿Cuál prefieres?";
    }

    private String serviceNames(JSONObject data) {
        JSONArray services = data.optJSONArray("services");
        if (services == null || services.isEmpty()) return "En este momento no tengo servicios activos para reservar.";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(services.length(), 4); i++) {
            names.add(services.getJSONObject(i).optString("name", "servicio"));
        }
        if (names.size() == 1) {
            return "Puedo ayudarte con " + names.get(0) + ". ¿Para qué día y hora te gustaría reservar?";
        }
        return "Puedo ayudarte con " + joinSpanish(names) + ". ¿Cuál te interesa?";
    }

    private String firstKnowledge(JSONObject data) {
        JSONArray results = data.optJSONArray("results");
        if (results == null || results.isEmpty()) return "No tengo esa información configurada. ¿Quieres consultar servicios o una reserva?";
        String content = results.getJSONObject(0).optString("content", "");
        return content.isBlank() ? "No tengo esa información configurada." : content;
    }

    private String sanitizeForSpeech(String text) {
        if (text == null || text.isBlank()) return "No pude responder en este momento.";
        String cleaned = text.replaceAll("[`*_#>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    private String friendlyDay(String rawDate, String timezone) {
        try {
            LocalDate date = LocalDate.parse(rawDate);
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            if (date.equals(today)) return "hoy";
            if (date.equals(today.plusDays(1))) return "mañana";
            return date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "CL")));
        } catch (Exception ignored) {
            return "ese día";
        }
    }

    private String friendlyLocalDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "el horario solicitado";
        try {
            java.time.OffsetDateTime value = java.time.OffsetDateTime.parse(raw);
            return value.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' H:mm", new Locale("es", "CL")));
        } catch (Exception ignored) {
            return "el horario solicitado";
        }
    }

    private String spokenTime(String raw) {
        try {
            LocalTime time = LocalTime.parse(raw);
            return time.getMinute() == 0
                    ? time.getHour() + " horas"
                    : String.format(Locale.ROOT, "%d:%02d", time.getHour(), time.getMinute());
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String joinSpanish(List<String> values) {
        if (values.isEmpty()) return "ninguna hora";
        if (values.size() == 1) return values.get(0);
        return String.join(", ", values.subList(0, values.size() - 1)) + " o " + values.get(values.size() - 1);
    }

    private boolean isFarewell(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("adiós") || normalized.contains("adios")
                || normalized.contains("hasta luego") || normalized.contains("chao")
                || normalized.contains("chau") || normalized.contains("eso es todo")
                || normalized.contains("nada más") || normalized.contains("nada mas");
    }

    private record ToolExecution(String callId, String name, String result) {}
}
