package cl.helvoca.telephony.twilio.trial;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TrialConversationStateService {
    private static final Logger log = LoggerFactory.getLogger(TrialConversationStateService.class);

    private final ConcurrentMap<UUID, State> states = new ConcurrentHashMap<>();

    public boolean beginInitialization(UUID callId) {
        State state = states.computeIfAbsent(callId, ignored -> new State());
        synchronized (state) {
            if (state.initialized) return false;
            state.initialized = true;
            return true;
        }
    }

    public String promptContext(UUID callId) {
        State state = states.get(callId);
        if (state == null || state.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\nEstado estructurado de esta llamada. Úsalo como memoria y no vuelvas a pedir datos ya conocidos:\n");
        if (state.serviceId != null) out.append("- serviceId seleccionado: ").append(state.serviceId).append('\n');
        if (state.serviceName != null) out.append("- servicio seleccionado: ").append(state.serviceName).append('\n');
        if (state.callerLookupDone) {
            if (Boolean.TRUE.equals(state.customerFound)) {
                out.append("- cliente asociado al teléfono: sí\n");
            } else {
                out.append("- cliente asociado al teléfono: no; pide su nombre de forma natural si hace falta para reservar\n");
            }
        }
        if (state.customerName != null) out.append("- nombre del cliente: ").append(state.customerName).append('\n');
        if (state.requestedStartAt != null) out.append("- horario solicitado: ").append(state.requestedStartAt).append('\n');
        if (state.available != null) out.append("- disponibilidad comprobada: ").append(state.available).append('\n');
        if (state.bookingConfirmed) out.append("- reserva confirmada: true\n");
        return out.toString();
    }

    public void observeToolResult(UUID callId, String toolName, String rawResult) {
        JSONObject result;
        try {
            result = new JSONObject(rawResult);
        } catch (Exception ignored) {
            log.info("Trial tool outcome call={} tool={} parseable=false", callId, toolName);
            return;
        }

        boolean success = result.optBoolean("success", false);
        JSONObject data = result.optJSONObject("data");
        logToolOutcome(callId, toolName, result, data, success);

        State state = states.computeIfAbsent(callId, ignored -> new State());
        if ("find_caller".equals(toolName)) {
            state.callerLookupDone = true;
        }
        if (!success || data == null) return;

        switch (toolName) {
            case "list_services" -> observeServices(state, data);
            case "find_caller" -> {
                state.customerFound = data.optBoolean("found", false);
                if (Boolean.TRUE.equals(state.customerFound)) {
                    setIfPresent(data, "name", value -> state.customerName = value);
                }
            }
            case "register_caller" -> {
                state.customerFound = true;
                state.callerLookupDone = true;
                setIfPresent(data, "name", value -> state.customerName = value);
            }
            case "check_booking_availability" -> {
                setIfPresent(data, "serviceId", value -> state.serviceId = value);
                setIfPresent(data, "serviceName", value -> state.serviceName = value);
                setIfPresent(data, "startAt", value -> state.requestedStartAt = value);
                state.available = data.optBoolean("available", false);
            }
            case "create_booking", "reschedule_booking" -> {
                setIfPresent(data, "service", value -> state.serviceName = value);
                setIfPresent(data, "serviceId", value -> state.serviceId = value);
                setIfPresent(data, "startAt", value -> state.requestedStartAt = value);
                state.available = true;
                state.bookingConfirmed = true;
            }
            case "cancel_booking" -> {
                setIfPresent(data, "service", value -> state.serviceName = value);
                setIfPresent(data, "startAt", value -> state.requestedStartAt = value);
                state.bookingConfirmed = false;
            }
            default -> {
            }
        }
    }

    public String timeoutFallback(UUID callId, String speech) {
        State state = states.get(callId);
        String normalized = speech == null ? "" : speech.toLowerCase(Locale.ROOT);
        boolean bookingIntent = normalized.contains("reserva") || normalized.contains("reservar")
                || normalized.contains("hora") || state != null && state.serviceId != null;

        if (bookingIntent && (state == null || !Boolean.TRUE.equals(state.customerFound))) {
            return "Claro. ¿A nombre de quién sería?";
        }
        if (bookingIntent && state != null && state.serviceId == null) {
            return "Claro. ¿Qué servicio te gustaría reservar?";
        }
        if (bookingIntent && state != null && state.requestedStartAt == null) {
            return "Perfecto. ¿Para qué día y hora te gustaría reservar?";
        }
        if (bookingIntent && state != null && Boolean.TRUE.equals(state.available) && !state.bookingConfirmed) {
            return "Ese horario está disponible. ¿Quieres que confirme la reserva?";
        }
        return "Perdón, tardé un poco. ¿Puedes repetir lo último?";
    }

    public String afterRegistrationPrompt(UUID callId) {
        State state = states.get(callId);
        if (state != null && state.requestedStartAt != null) {
            return "Perfecto, ya tengo tu nombre. ¿Quieres que confirme la reserva para el horario que indicaste?";
        }
        return "Perfecto, ya tengo tu nombre. ¿Para qué día y hora te gustaría reservar?";
    }

    public void clear(UUID callId) {
        states.remove(callId);
    }

    private static void logToolOutcome(UUID callId,
                                       String toolName,
                                       JSONObject result,
                                       JSONObject data,
                                       boolean success) {
        if (!success) {
            JSONObject error = result.optJSONObject("error");
            String errorCode = error == null ? "UNKNOWN" : error.optString("code", "UNKNOWN");
            log.info("Trial tool outcome call={} tool={} success=false errorCode={}", callId, toolName, errorCode);
            return;
        }

        if (("create_booking".equals(toolName)
                || "reschedule_booking".equals(toolName)
                || "cancel_booking".equals(toolName)) && data != null) {
            log.info("Trial tool outcome call={} tool={} success=true bookingId={} status={}",
                    callId,
                    toolName,
                    data.optString("bookingId", "unknown"),
                    data.optString("status", "unknown"));
            return;
        }

        log.info("Trial tool outcome call={} tool={} success=true", callId, toolName);
    }

    private static void observeServices(State state, JSONObject data) {
        JSONArray services = data.optJSONArray("services");
        if (services == null || services.length() != 1) return;
        JSONObject service = services.optJSONObject(0);
        if (service == null) return;
        setIfPresent(service, "id", value -> state.serviceId = value);
        setIfPresent(service, "name", value -> state.serviceName = value);
    }

    private static void setIfPresent(JSONObject object, String key, java.util.function.Consumer<String> consumer) {
        if (!object.has(key) || object.isNull(key)) return;
        String value = object.optString(key, "").trim();
        if (!value.isBlank()) consumer.accept(value);
    }

    private static final class State {
        private boolean initialized;
        private boolean callerLookupDone;
        private Boolean customerFound;
        private String serviceId;
        private String serviceName;
        private String customerName;
        private String requestedStartAt;
        private Boolean available;
        private boolean bookingConfirmed;

        private boolean isEmpty() {
            return !callerLookupDone && serviceId == null && serviceName == null && customerName == null
                    && requestedStartAt == null && available == null && !bookingConfirmed;
        }
    }
}
