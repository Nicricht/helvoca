package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolDefinitions;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReceptionistSimulatorService {
    public static final String PROVIDER_ID = "simulator";
    private static final Logger log = LoggerFactory.getLogger(ReceptionistSimulatorService.class);
    private static final int MAX_TURNS = 24;

    private final TenantProvider tenantProvider;
    private final BusinessRepository businesses;
    private final CallSessionRepository calls;
    private final CallTranscriptRepository transcriptRepository;
    private final CallTranscriptService transcriptWriter;
    private final CallActionRepository actions;
    private final CallSummaryService summaries;
    private final RealtimeToolService realTools;
    private final SimulatorToolExecutor simulatorTools;
    private final SimulatorStateService state;
    private final OpenAiRealtimeProperties openAi;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public ReceptionistSimulatorService(TenantProvider tenantProvider,
                                        BusinessRepository businesses,
                                        CallSessionRepository calls,
                                        CallTranscriptRepository transcriptRepository,
                                        CallTranscriptService transcriptWriter,
                                        CallActionRepository actions,
                                        CallSummaryService summaries,
                                        RealtimeToolService realTools,
                                        SimulatorToolExecutor simulatorTools,
                                        SimulatorStateService state,
                                        OpenAiRealtimeProperties openAi) {
        this.tenantProvider = tenantProvider;
        this.businesses = businesses;
        this.calls = calls;
        this.transcriptRepository = transcriptRepository;
        this.transcriptWriter = transcriptWriter;
        this.actions = actions;
        this.summaries = summaries;
        this.realTools = realTools;
        this.simulatorTools = simulatorTools;
        this.state = state;
        this.openAi = openAi;
    }

    @Transactional
    public SessionResponse start() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        if (!openAi.hasApiKey()) throw new IllegalStateException("OpenAI is not configured");

        UUID token = UUID.randomUUID();
        Instant now = Instant.now();
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setTelephonyProvider(PROVIDER_ID);
        call.setAiProvider("openai");
        call.setProviderCallId("simulator:" + token);
        call.setCallerNumber("web-simulator");
        call.setDestinationNumber("web-simulator");
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(now);
        call.setAnsweredAt(now);
        call.setStreamSid("simulator:" + token);
        call.setStreamStartedAt(now);
        call = calls.saveAndFlush(call);

        state.start(call.getId());
        String greeting = "Hola, soy la recepcionista virtual de " + business.getName() + ". ¿En qué puedo ayudarte?";
        transcriptWriter.append(call.getId(), "ASSISTANT", greeting);
        return new SessionResponse(call.getId(), greeting, true, false);
    }

    public MessageResponse message(UUID sessionId, String message) {
        UUID businessId = tenantProvider.requireBusinessId();
        CallSession call = requireSession(sessionId, businessId);
        if (call.getStatus().terminal()) throw new IllegalArgumentException("La prueba ya terminó.");
        String clean = message == null ? "" : message.trim();
        if (clean.isBlank()) throw new IllegalArgumentException("Escribe un mensaje para continuar la prueba.");

        RealtimeCallContext context = context(call);
        transcriptWriter.append(sessionId, "USER", clean);
        int turns = userTurns(sessionId);
        if (turns > MAX_TURNS) {
            String text = "Esta prueba llegó al límite de turnos. Puedes iniciar una nueva para seguir probando.";
            transcriptWriter.append(sessionId, "ASSISTANT", text);
            finishInternal(call);
            return response(call, text, true);
        }

        if (isFarewell(clean)) {
            String text = "Gracias por probar Helvoca. La simulación terminó y no modificó datos reales del negocio.";
            transcriptWriter.append(sessionId, "ASSISTANT", text);
            finishInternal(call);
            return response(call, text, true);
        }

        String reply;
        try {
            String instructions = realTools.buildInstructions(context)
                    + state.promptContext(sessionId)
                    + "\n" + """
                    Estás en el simulador web seguro de Helvoca.
                    Actúa exactamente como la recepcionista del negocio, no como un asistente técnico.
                    Las consultas usan la configuración real del negocio, pero toda acción de escritura es una simulación aislada.
                    Nunca afirmes que una reserva, solicitud, cliente o pregunta fue guardada realmente.
                    Cuando una acción simulada tenga éxito, dilo como algo que ocurriría en una llamada real.
                    No menciones nombres de herramientas, UUID, backend, base de datos ni detalles técnicos.
                    Responde en español natural, breve y sin markdown, con un máximo de 55 palabras.
                    Si el cliente quiere reservar y aún no está identificado en la simulación, pregunta su nombre de forma natural.
                    Si pide horarios de un día usa list_available_slots. Si entrega una hora exacta usa check_booking_availability.
                    Para crear, reprogramar o cancelar usa las herramientas correspondientes y confía solo en su resultado.
                    Para necesidades de seguimiento no reservables usa create_request.
                    Si no conoces una respuesta, busca primero en search_knowledge y luego usa record_unanswered_question si sigue sin respuesta.
                    Si pide hablar con una persona usa transfer_to_human; en esta prueba solo debes explicar que la transferencia ocurriría en una llamada real.
                    """;
            JSONObject body = new JSONObject()
                    .put("model", openAi.getTrialModel())
                    .put("instructions", instructions)
                    .put("tools", responseTools())
                    .put("tool_choice", "auto")
                    .put("input", buildHistory(sessionId))
                    .put("max_output_tokens", 180);
            JSONObject first = send(body);
            JSONArray functionCalls = functionCalls(first);
            String text = extractOutputText(first);
            if (!functionCalls.isEmpty()) {
                List<ToolExecution> executions = executeTools(context, functionCalls);
                reply = summarize(context, executions);
            } else if (text != null && !text.isBlank()) {
                reply = sanitize(text);
            } else {
                reply = "No entendí del todo. ¿Puedes decírmelo de otra forma?";
            }
        } catch (Exception e) {
            log.warn("Receptionist simulator failed call={}: {}", sessionId, e.getMessage());
            reply = "No pude completar esa parte de la prueba ahora. Puedes intentarlo nuevamente sin que se haya modificado ningún dato real.";
        }

        transcriptWriter.append(sessionId, "ASSISTANT", reply);
        return response(call, reply, false);
    }

    @Transactional
    public SessionResponse finish(UUID sessionId) {
        UUID businessId = tenantProvider.requireBusinessId();
        CallSession call = requireSession(sessionId, businessId);
        finishInternal(call);
        return new SessionResponse(call.getId(), "Prueba finalizada.", false, true);
    }

    private JSONObject send(JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(openAi.getResponsesUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + openAi.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenAI simulator request failed with HTTP " + response.statusCode());
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
            JSONObject call = calls.optJSONObject(i);
            if (call == null) continue;
            String callId = call.optString("call_id", "");
            String name = call.optString("name", "");
            String arguments = call.optString("arguments", "{}");
            if (callId.isBlank() || name.isBlank()) continue;
            out.add(new ToolExecution(name, simulatorTools.execute(context, name, arguments)));
        }
        return out;
    }

    private String summarize(RealtimeCallContext context, List<ToolExecution> executions) {
        if (executions.isEmpty()) return "No pude completar esa parte de la prueba. ¿Quieres intentarlo de otra forma?";
        ToolExecution execution = preferred(executions);
        JSONObject result;
        try { result = new JSONObject(execution.result()); }
        catch (Exception e) { return "No pude interpretar el resultado de la prueba."; }

        if (!result.optBoolean("success", false)) {
            JSONObject error = result.optJSONObject("error");
            String code = error == null ? "" : error.optString("code", "");
            return switch (code) {
                case "CUSTOMER_NOT_REGISTERED" -> "Claro. ¿A nombre de quién sería?";
                case "BOOKING_SLOT_UNAVAILABLE" -> "Ese horario no está disponible. ¿Quieres que busque otra alternativa?";
                case "BUSINESS_CLOSED" -> "Ese horario está fuera del horario de atención. ¿Quieres que busque otra hora?";
                case "BOOKING_NOT_FOUND" -> "No encuentro esa reserva dentro de esta prueba. ¿Quieres revisar las reservas simuladas?";
                case "BOOKING_CANCELLED" -> "Esa reserva de prueba ya está cancelada.";
                default -> {
                    String message = error == null ? "" : error.optString("message", "");
                    yield message.isBlank() ? "No pude completar esa acción en la prueba." : sanitize(message);
                }
            };
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) return "Listo dentro de la simulación.";
        return switch (execution.name()) {
            case "create_booking" -> "En una llamada real, la reserva quedaría confirmada para "
                    + friendlyDateTime(data.optString("localStart", data.optString("startAt", ""))) + ".";
            case "reschedule_booking" -> "En una llamada real, la reserva quedaría reprogramada para "
                    + friendlyDateTime(data.optString("localStart", data.optString("startAt", ""))) + ".";
            case "cancel_booking" -> "En una llamada real, esa reserva quedaría cancelada.";
            case "list_customer_bookings" -> bookingList(data);
            case "check_booking_availability" -> data.optBoolean("available", false)
                    ? "Sí, ese horario está disponible. ¿Quieres que simule la reserva?"
                    : "Ese horario no está disponible. ¿Quieres que busque otra alternativa?";
            case "list_available_slots" -> availableSlots(data);
            case "register_caller" -> "Perfecto, ya tengo el nombre para esta prueba. ¿Qué necesitas?";
            case "find_caller" -> data.optBoolean("found", false)
                    ? "Perfecto, ya tengo tus datos de prueba."
                    : "¿A nombre de quién sería?";
            case "list_services" -> serviceNames(data);
            case "get_business_information" -> "Estás hablando con " + data.optString("name", "este negocio") + ".";
            case "search_knowledge" -> knowledgeAnswer(data);
            case "create_request" -> "En una llamada real, la solicitud quedaría registrada para seguimiento del negocio.";
            case "record_unanswered_question" -> "No tengo esa información confirmada. En una llamada real dejaría la pregunta pendiente para que el negocio la responda.";
            case "transfer_to_human" -> "En una llamada real, ahora te transferiría con una persona del negocio.";
            default -> "Listo dentro de la simulación.";
        };
    }

    private ToolExecution preferred(List<ToolExecution> executions) {
        for (int i = executions.size() - 1; i >= 0; i--) {
            ToolExecution item = executions.get(i);
            try {
                if (new JSONObject(item.result()).optBoolean("success", false)) return item;
            } catch (Exception ignored) { }
        }
        return executions.get(executions.size() - 1);
    }

    private String bookingList(JSONObject data) {
        JSONArray list = data.optJSONArray("bookings");
        if (list == null || list.isEmpty()) return "No hay reservas activas dentro de esta prueba.";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(3, list.length()); i++) {
            JSONObject booking = list.optJSONObject(i);
            if (booking == null) continue;
            values.add(booking.optString("service", "servicio") + " el "
                    + friendlyDateTime(booking.optString("localStart", booking.optString("startAt", ""))));
        }
        return "Dentro de esta prueba tienes " + list.length() + " reserva(s): " + joinSpanish(values) + ".";
    }

    private String availableSlots(JSONObject data) {
        JSONArray slots = data.optJSONArray("slots");
        if (slots == null || slots.isEmpty()) return "No encontré horas disponibles ese día. ¿Quieres consultar otra fecha?";
        List<String> times = new ArrayList<>();
        for (int i = 0; i < Math.min(5, slots.length()); i++) {
            JSONObject slot = slots.optJSONObject(i);
            if (slot != null) times.add(spokenTime(slot.optString("localTime", "")));
        }
        return "Tengo disponibilidad a las " + joinSpanish(times) + ". ¿Cuál prefieres?";
    }

    private String serviceNames(JSONObject data) {
        JSONArray services = data.optJSONArray("services");
        if (services == null || services.isEmpty()) return "No hay servicios activos configurados.";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(4, services.length()); i++) {
            JSONObject item = services.optJSONObject(i);
            if (item != null) names.add(item.optString("name", "servicio"));
        }
        return "Puedo ayudarte con " + joinSpanish(names) + ". ¿Cuál te interesa?";
    }

    private String knowledgeAnswer(JSONObject data) {
        JSONArray results = data.optJSONArray("results");
        if (results == null || results.isEmpty()) return "No tengo esa información confirmada.";
        String content = results.getJSONObject(0).optString("content", "");
        return content.isBlank() ? "No tengo esa información confirmada." : sanitize(content);
    }

    private String buildHistory(UUID callId) {
        List<CallTranscript> items = transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(callId);
        int start = Math.max(0, items.size() - 16);
        StringBuilder out = new StringBuilder("Conversación de prueba hasta ahora:\n");
        for (int i = start; i < items.size(); i++) {
            CallTranscript item = items.get(i);
            out.append(item.getSpeaker()).append(": ").append(item.getContent()).append('\n');
            if (out.length() > 8_000) break;
        }
        out.append("Responde a la última intervención del cliente.");
        return out.toString();
    }

    private JSONArray functionCalls(JSONObject root) {
        JSONArray output = root.optJSONArray("output");
        JSONArray calls = new JSONArray();
        if (output == null) return calls;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null && "function_call".equals(item.optString("type"))) calls.put(item);
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

    private MessageResponse response(CallSession call, String text, boolean ended) {
        CallSession fresh = calls.findById(call.getId()).orElse(call);
        long actionCount = actions.findAllByCallIdOrderByCreatedAtAsc(call.getId()).size();
        return new MessageResponse(call.getId(), text, fresh.getResolution(), actionCount, ended);
    }

    private CallSession requireSession(UUID id, UUID businessId) {
        CallSession call = calls.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Simulation not found"));
        if (!PROVIDER_ID.equals(call.getTelephonyProvider())) {
            throw new IllegalArgumentException("La sesión no corresponde al simulador.");
        }
        return call;
    }

    private RealtimeCallContext context(CallSession call) {
        return new RealtimeCallContext(
                call.getId(), call.getBusinessId(), null,
                call.getCallerNumber(), call.getDestinationNumber(), call.getStreamSid());
    }

    private void finishInternal(CallSession call) {
        if (call.getStatus().terminal()) return;
        Instant now = Instant.now();
        call.setStatus(CallStatus.COMPLETED);
        call.setEndedAt(now);
        call.setStreamEndedAt(now);
        if (call.getStartedAt() != null) {
            call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), now).toSeconds()));
        }
        calls.save(call);
        state.finish(call.getId());
        summaries.generate(call.getId());
    }

    private int userTurns(UUID callId) {
        int count = 0;
        for (CallTranscript item : transcriptRepository.findAllByCallIdOrderBySequenceNumberAsc(callId)) {
            if ("USER".equalsIgnoreCase(item.getSpeaker())) count++;
        }
        return count;
    }

    private static boolean isFarewell(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return value.contains("adiós") || value.contains("adios") || value.contains("hasta luego")
                || value.contains("chao") || value.contains("chau") || value.contains("eso es todo")
                || value.contains("nada más") || value.contains("nada mas");
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) return "No pude responder en este momento.";
        String cleaned = text.replaceAll("[`*_#>]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 700 ? cleaned : cleaned.substring(0, 700);
    }

    private static String friendlyDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "el horario solicitado";
        try {
            return OffsetDateTime.parse(raw)
                    .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' H:mm", new Locale("es", "CL")));
        } catch (Exception ignored) {
            return "el horario solicitado";
        }
    }

    private static String spokenTime(String raw) {
        try {
            LocalTime time = LocalTime.parse(raw);
            return time.getMinute() == 0 ? time.getHour() + " horas" : String.format(Locale.ROOT, "%d:%02d", time.getHour(), time.getMinute());
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String joinSpanish(List<String> values) {
        if (values.isEmpty()) return "ninguno";
        if (values.size() == 1) return values.get(0);
        return String.join(", ", values.subList(0, values.size() - 1)) + " o " + values.get(values.size() - 1);
    }

    private record ToolExecution(String name, String result) {}

    public record SessionResponse(UUID sessionId, String greeting, boolean active, boolean ended) {}

    public record MessageResponse(UUID sessionId, String reply, String resolution, long actionCount, boolean ended) {}
}
