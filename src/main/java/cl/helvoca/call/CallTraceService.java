package cl.helvoca.call;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CallTraceService {
    private static final Map<String, Integer> RESOLUTION_PRIORITY = Map.ofEntries(
            Map.entry("INFORMATION_ONLY", 10),
            Map.entry("CUSTOMER_REGISTERED", 20),
            Map.entry("UNANSWERED_QUESTION", 40),
            Map.entry("REQUEST_CREATED", 80),
            Map.entry("BOOKING_CREATED", 90),
            Map.entry("BOOKING_RESCHEDULED", 90),
            Map.entry("BOOKING_CANCELLED", 90),
            Map.entry("HUMAN_TRANSFERRED", 100),
            Map.entry("FAILED", 100)
    );

    private final CallSessionRepository calls;
    private final CallActionRepository actions;

    public CallTraceService(CallSessionRepository calls, CallActionRepository actions) {
        this.calls = calls;
        this.actions = actions;
    }

    @Transactional
    public void recordTool(UUID businessId, UUID callId, String toolName, JSONObject result) {
        CallSession call = requireCall(businessId, callId);
        boolean success = result.optBoolean("success", false);
        JSONObject data = result.optJSONObject("data");
        JSONObject error = result.optJSONObject("error");

        CallAction action = new CallAction();
        action.setBusinessId(businessId);
        action.setCallId(callId);
        action.setActionType(actionType(toolName));
        action.setSuccess(success);
        if (error != null) action.setErrorCode(error.optString("code", null));
        if (data != null) {
            String entityType = entityType(toolName);
            UUID entityId = entityId(toolName, data);
            action.setEntityType(entityType);
            action.setEntityId(entityId);
            action.setDetail(detail(toolName, data));
        }
        actions.save(action);

        if (success) {
            String resolution = resolution(toolName);
            if (resolution != null) markResolution(call, resolution);
        }
    }

    @Transactional
    public void recordHumanTransfer(UUID businessId, UUID callId, boolean accepted, String targetPhone) {
        CallSession call = requireCall(businessId, callId);
        CallAction action = new CallAction();
        action.setBusinessId(businessId);
        action.setCallId(callId);
        action.setActionType("HUMAN_TRANSFER");
        action.setSuccess(accepted);
        action.setDetail(accepted ? "Transferencia aceptada por el proveedor telefónico" : "El proveedor telefónico rechazó la transferencia");
        action.setErrorCode(accepted ? null : "HUMAN_TRANSFER_FAILED");
        actions.save(action);
        if (accepted) markResolution(call, "HUMAN_TRANSFERRED");
    }

    private CallSession requireCall(UUID businessId, UUID callId) {
        return calls.findByIdAndBusinessId(callId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("La llamada no pertenece al negocio."));
    }

    private void markResolution(CallSession call, String next) {
        String current = call.getResolution();
        int currentPriority = current == null ? -1 : RESOLUTION_PRIORITY.getOrDefault(current, 0);
        int nextPriority = RESOLUTION_PRIORITY.getOrDefault(next, 0);
        if (current == null || nextPriority >= currentPriority) call.setResolution(next);
    }

    private static String actionType(String toolName) {
        return switch (toolName) {
            case "get_business_information" -> "BUSINESS_INFORMATION";
            case "list_services" -> "SERVICES_LISTED";
            case "search_knowledge" -> "KNOWLEDGE_SEARCH";
            case "find_caller" -> "CALLER_LOOKUP";
            case "register_caller" -> "CUSTOMER_REGISTERED";
            case "list_available_slots" -> "AVAILABILITY_LISTED";
            case "check_booking_availability" -> "AVAILABILITY_CHECKED";
            case "create_booking" -> "BOOKING_CREATED";
            case "list_customer_bookings" -> "BOOKINGS_LISTED";
            case "reschedule_booking" -> "BOOKING_RESCHEDULED";
            case "cancel_booking" -> "BOOKING_CANCELLED";
            case "create_request" -> "REQUEST_CREATED";
            case "record_unanswered_question" -> "UNANSWERED_QUESTION_RECORDED";
            case "transfer_to_human" -> "TRANSFER_REQUESTED";
            default -> toolName.toUpperCase();
        };
    }

    private static String resolution(String toolName) {
        return switch (toolName) {
            case "get_business_information", "list_services", "search_knowledge" -> "INFORMATION_ONLY";
            case "register_caller" -> "CUSTOMER_REGISTERED";
            case "create_booking" -> "BOOKING_CREATED";
            case "reschedule_booking" -> "BOOKING_RESCHEDULED";
            case "cancel_booking" -> "BOOKING_CANCELLED";
            case "create_request" -> "REQUEST_CREATED";
            case "record_unanswered_question" -> "UNANSWERED_QUESTION";
            default -> null;
        };
    }

    private static String entityType(String toolName) {
        return switch (toolName) {
            case "register_caller" -> "CUSTOMER";
            case "create_booking", "reschedule_booking", "cancel_booking" -> "BOOKING";
            case "create_request" -> "BUSINESS_REQUEST";
            case "record_unanswered_question" -> "UNANSWERED_QUESTION";
            default -> null;
        };
    }

    private static UUID entityId(String toolName, JSONObject data) {
        String key = switch (toolName) {
            case "register_caller" -> "customerId";
            case "create_booking", "reschedule_booking", "cancel_booking" -> "bookingId";
            case "create_request" -> "requestId";
            case "record_unanswered_question" -> "questionId";
            default -> null;
        };
        if (key == null) return null;
        String raw = data.optString(key, null);
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); }
        catch (Exception ignored) { return null; }
    }

    private static String detail(String toolName, JSONObject data) {
        return switch (toolName) {
            case "register_caller" -> data.optString("name", "Cliente registrado");
            case "create_booking", "reschedule_booking", "cancel_booking" -> {
                String service = data.optString("service", "Reserva");
                String when = data.optString("localStart", data.optString("startAt", ""));
                yield when.isBlank() ? service : service + " · " + when;
            }
            case "create_request" -> data.optString("title", "Solicitud creada");
            case "record_unanswered_question" -> data.optString("question", "Pregunta registrada");
            case "transfer_to_human" -> "Transferencia solicitada";
            default -> null;
        };
    }
}
