package cl.helvoca.ai.realtime;

import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationStateService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Backend-owned booking dialogue state.
 *
 * The LLM may phrase the conversation, but it does not own which booking fields
 * are already resolved. Successful tool results close fields deterministically
 * and every relevant response carries the current state plus the only valid
 * next conversational action.
 */
@Service
public class BookingConversationStateMachine {
    private static final Set<String> RELEVANT_TOOLS = Set.of(
            "list_services",
            "find_caller",
            "register_caller",
            "list_available_slots",
            "check_booking_availability",
            "create_booking");

    private final ConversationStateService conversationState;

    public BookingConversationStateMachine(ConversationStateService conversationState) {
        this.conversationState = conversationState;
    }

    public JSONObject decorate(RealtimeCallContext context,
                               String toolName,
                               JSONObject args,
                               JSONObject result) {
        if (context == null || context.businessId() == null || context.callId() == null
                || toolName == null || result == null || !RELEVANT_TOOLS.contains(toolName)) {
            return result;
        }

        JSONObject safeArgs = args == null ? new JSONObject() : args;
        JSONObject copy = new JSONObject(result.toString());
        ConversationOperationState state = null;

        if (copy.optBoolean("success", false)) {
            JSONObject data = copy.optJSONObject("data");
            LinkedHashMap<String, Object> patch = patchFor(toolName, safeArgs, data);
            UUID activeOperationId = operationId(data);
            if (!patch.isEmpty() || activeOperationId != null) {
                state = conversationState.apply(
                        context.businessId(),
                        context.callId(),
                        BusinessOrder.Source.VOICE,
                        activeOperationId,
                        patch);
            }
        }

        if (state == null) {
            state = conversationState.find(
                    context.businessId(),
                    context.callId(),
                    BusinessOrder.Source.VOICE);
        }
        if (state != null) {
            copy.put("conversationState", snapshot(state));
        }
        return copy;
    }

    static LinkedHashMap<String, Object> patchFor(String toolName,
                                                   JSONObject args,
                                                   JSONObject data) {
        LinkedHashMap<String, Object> patch = new LinkedHashMap<>();
        if (data == null) return patch;

        switch (toolName) {
            case "list_services" -> patch.put("bookingCatalogLoaded", true);
            case "find_caller" -> {
                boolean found = data.optBoolean("found", false);
                patch.put("bookingCustomerKnown", found);
                if (found) {
                    putIfPresent(patch, "bookingCustomerName", data.optString("name", null));
                    putIfPresent(patch, "bookingCustomerPhone", data.optString("phone", null));
                } else {
                    patch.put("bookingCustomerName", null);
                    patch.put("bookingCustomerPhone", null);
                }
            }
            case "register_caller" -> {
                patch.put("bookingCustomerKnown", true);
                putIfPresent(patch, "bookingCustomerName", data.optString("name", null));
                putIfPresent(patch, "bookingCustomerPhone", data.optString("phone", null));
            }
            case "list_available_slots" -> {
                patch.put("intent", "BOOKING");
                putIfPresent(patch, "serviceId", data.optString("serviceId", null));
                putIfPresent(patch, "serviceName", data.optString("serviceName", null));
                putIfPresent(patch, "bookingAvailabilityDate", data.optString("date", args.optString("date", null)));
                patch.put("bookingSlotValidated", false);
                patch.put("startAt", null);
                patch.put("localStart", null);
                patch.put("bookingFlowStage", "SELECTING_SLOT");
                patch.put("confirmationPending", false);
            }
            case "check_booking_availability" -> {
                patch.put("intent", "BOOKING");
                putIfPresent(patch, "serviceId", data.optString("serviceId", null));
                putIfPresent(patch, "serviceName", data.optString("serviceName", null));
                boolean available = data.optBoolean("available", false);
                patch.put("bookingSlotValidated", available);
                if (available) {
                    putIfPresent(patch, "startAt", data.optString("startAt", null));
                    putIfPresent(patch, "localStart", data.optString("localStart", null));
                    patch.put("bookingFlowStage", "SLOT_VALIDATED");
                } else {
                    patch.put("startAt", null);
                    patch.put("localStart", null);
                    patch.put("bookingFlowStage", "SELECTING_SLOT");
                }
                patch.put("confirmationPending", false);
            }
            case "create_booking" -> {
                patch.put("intent", "BOOKING");
                putIfPresent(patch, "serviceId", data.optString("serviceId", null));
                String serviceName = data.optString("service", data.optString("serviceName", null));
                putIfPresent(patch, "serviceName", serviceName);
                putIfPresent(patch, "startAt", data.optString("startAt", null));
                putIfPresent(patch, "localStart", data.optString("localStart", null));
                patch.put("bookingSlotValidated", true);

                String bookingId = data.optString("bookingId", null);
                boolean confirmed = bookingId != null && !bookingId.isBlank();
                if (confirmed) {
                    patch.put("bookingId", bookingId);
                    patch.put("bookingStatus", data.optString("status", "CONFIRMED"));
                    patch.put("bookingFlowStage", "CONFIRMED");
                    patch.put("confirmationPending", false);
                } else if (data.optBoolean("requiresConfirmation", false)) {
                    patch.put("bookingId", null);
                    patch.put("bookingStatus", null);
                    patch.put("bookingFlowStage", "WAITING_CONFIRMATION");
                    patch.put("confirmationPending", true);
                    putIfPresent(patch, "operationId", data.optString("operationId", null));
                }
            }
            default -> {
            }
        }
        return patch;
    }

    static JSONObject snapshot(ConversationOperationState state) {
        Map<String, Object> values = state.getState() == null ? Map.of() : state.getState();

        boolean bookingActive = "BOOKING".equals(String.valueOf(values.get("intent")));
        boolean serviceKnown = present(values, "serviceId");
        boolean slotKnown = Boolean.TRUE.equals(values.get("bookingSlotValidated"))
                && present(values, "startAt");
        boolean customerKnown = Boolean.TRUE.equals(values.get("bookingCustomerKnown"));
        boolean confirmationPending = Boolean.TRUE.equals(values.get("confirmationPending"));
        boolean confirmed = "CONFIRMED".equals(String.valueOf(values.get("bookingFlowStage")))
                && present(values, "bookingId");

        JSONArray closed = new JSONArray();
        if (serviceKnown) closed.put("service");
        if (slotKnown) {
            closed.put("date");
            closed.put("time");
        }
        if (customerKnown) {
            closed.put("name");
            closed.put("phone");
        }
        if (confirmed) closed.put("confirmation");

        String nextRequiredField;
        String nextAction;
        if (!bookingActive) {
            nextRequiredField = "NONE";
            nextAction = "CONTINUE_CURRENT_INTENT";
        } else if (confirmed) {
            nextRequiredField = "NONE";
            nextAction = "BOOKING_COMPLETE";
        } else if (confirmationPending) {
            nextRequiredField = "CONFIRMATION";
            nextAction = "ASK_CONFIRMATION_ONCE";
        } else if (!serviceKnown) {
            nextRequiredField = "SERVICE";
            nextAction = "ASK_SERVICE";
        } else if (!slotKnown) {
            nextRequiredField = "DATE_TIME";
            nextAction = "ASK_OR_VALIDATE_DATE_TIME";
        } else if (!customerKnown) {
            nextRequiredField = "CUSTOMER_IDENTITY";
            nextAction = "IDENTIFY_CUSTOMER";
        } else {
            nextRequiredField = "NONE";
            nextAction = "CREATE_BOOKING_PROPOSAL";
        }

        JSONObject out = new JSONObject()
                .put("source", "BACKEND")
                .put("revision", state.getRevision() == null ? 1 : state.getRevision())
                .put("closedFields", closed)
                .put("nextRequiredField", nextRequiredField)
                .put("nextAction", nextAction)
                .put("confirmationPending", confirmationPending)
                .put("bookingConfirmed", confirmed);

        copyIfPresent(values, out, "serviceId");
        copyIfPresent(values, out, "serviceName");
        copyIfPresent(values, out, "startAt");
        copyIfPresent(values, out, "localStart");
        copyIfPresent(values, out, "bookingCustomerName");
        copyIfPresent(values, out, "bookingCustomerPhone");
        copyIfPresent(values, out, "operationId");
        copyIfPresent(values, out, "bookingId");
        copyIfPresent(values, out, "bookingFlowStage");
        return out;
    }

    private static UUID operationId(JSONObject data) {
        if (data == null) return null;
        String value = data.optString("operationId", null);
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static boolean present(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private static void copyIfPresent(Map<String, Object> state, JSONObject target, String key) {
        Object value = state.get(key);
        if (value != null && !String.valueOf(value).isBlank()) target.put(key, value);
    }
}
