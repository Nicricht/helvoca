package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.call.CallTraceService;
import cl.helvoca.request.RequestPriority;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Executes the same receptionist tool contract while keeping mutations inside
 * {@link SimulatorStateService}. Read-only calls are delegated to the real
 * backend so the test uses the tenant's actual services, hours and knowledge.
 */
@Service
public class SimulatorToolExecutor {
    private final RealtimeToolService realTools;
    private final SimulatorStateService state;
    private final CallTraceService trace;

    public SimulatorToolExecutor(RealtimeToolService realTools,
                                 SimulatorStateService state,
                                 CallTraceService trace) {
        this.realTools = realTools;
        this.state = state;
        this.trace = trace;
    }

    public String execute(RealtimeCallContext context, String toolName, String rawArguments) {
        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            return switch (toolName) {
                case "find_caller" -> traced(context, toolName, findCaller(context));
                case "register_caller" -> traced(context, toolName, registerCaller(context, args));
                case "check_booking_availability" -> checkAvailability(context, rawArguments);
                case "list_available_slots" -> listAvailableSlots(context, rawArguments);
                case "create_booking" -> traced(context, toolName, createBooking(context, args));
                case "list_customer_bookings" -> traced(context, toolName, listBookings(context));
                case "reschedule_booking" -> traced(context, toolName, rescheduleBooking(context, args));
                case "cancel_booking" -> traced(context, toolName, cancelBooking(context, args));
                case "create_request" -> traced(context, toolName, createRequest(context, args));
                case "record_unanswered_question" -> traced(context, toolName, recordQuestion(context, args));
                case "transfer_to_human" -> realTools.execute(context, toolName, rawArguments);
                default -> realTools.execute(context, toolName, rawArguments);
            };
        } catch (IllegalArgumentException e) {
            return traced(context, toolName, error("INVALID_ARGUMENT", e.getMessage()));
        } catch (Exception e) {
            return traced(context, toolName, error("SIMULATOR_TOOL_FAILED", "No pude completar esa acción dentro de la simulación."));
        }
    }

    private JSONObject findCaller(RealtimeCallContext context) {
        var customer = state.customer(context.callId());
        if (customer == null) return success(new JSONObject().put("found", false));
        return success(new JSONObject()
                .put("found", true)
                .put("id", customer.id().toString())
                .put("name", customer.name())
                .put("phone", "simulator")
                .put("email", customer.email() == null ? JSONObject.NULL : customer.email()));
    }

    private JSONObject registerCaller(RealtimeCallContext context, JSONObject args) {
        String name = required(args, "name").trim();
        if (name.isBlank()) throw new IllegalArgumentException("El nombre no puede estar vacío.");
        String email = optional(args, "email");
        var customer = state.registerCustomer(context.callId(), name, email);
        return success(new JSONObject()
                .put("customerId", customer.id().toString())
                .put("name", customer.name())
                .put("phone", "simulator"));
    }

    private String checkAvailability(RealtimeCallContext context, String rawArguments) {
        String raw = realTools.execute(context, "check_booking_availability", rawArguments);
        JSONObject result = parse(raw);
        if (!result.optBoolean("success", false)) return raw;
        JSONObject data = result.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) return raw;
        UUID serviceId = uuid(data.optString("serviceId", null));
        Instant startAt = instant(data.optString("startAt", null));
        Instant endAt = instant(data.optString("endAt", null));
        if (state.overlaps(context.callId(), serviceId, startAt, endAt, null)) {
            data.put("available", false);
            data.put("simulatorConflict", true);
        }
        return result.toString();
    }

    private String listAvailableSlots(RealtimeCallContext context, String rawArguments) {
        String raw = realTools.execute(context, "list_available_slots", rawArguments);
        JSONObject result = parse(raw);
        if (!result.optBoolean("success", false)) return raw;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return raw;
        UUID serviceId = uuid(data.optString("serviceId", null));
        JSONArray source = data.optJSONArray("slots");
        if (source == null) return raw;
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject slot = source.optJSONObject(i);
            if (slot == null) continue;
            Instant start = instant(slot.optString("startAt", null));
            Instant end = instant(slot.optString("endAt", null));
            if (!state.overlaps(context.callId(), serviceId, start, end, null)) filtered.put(slot);
        }
        data.put("slots", filtered);
        return result.toString();
    }

    private JSONObject createBooking(RealtimeCallContext context, JSONObject args) {
        if (state.customer(context.callId()) == null) {
            return error("CUSTOMER_NOT_REGISTERED", "Necesito el nombre del cliente antes de confirmar la reserva de prueba.");
        }
        String serviceIdRaw = required(args, "serviceId");
        String startAtRaw = required(args, "startAt");
        JSONObject availabilityArgs = new JSONObject()
                .put("serviceId", serviceIdRaw)
                .put("startAt", startAtRaw);
        JSONObject availability = parse(checkAvailability(context, availabilityArgs.toString()));
        if (!availability.optBoolean("success", false)) return availability;
        JSONObject data = availability.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario no está disponible.");
        }

        UUID serviceId = uuid(data.optString("serviceId", serviceIdRaw));
        Instant startAt = instant(data.optString("startAt", startAtRaw));
        Instant endAt = instant(data.optString("endAt", null));
        String localStart = data.optString("localStart", startAt.toString());
        String serviceName = data.optString("serviceName", "Servicio");
        var booking = state.createBooking(context.callId(), serviceId, serviceName, startAt, endAt, localStart);
        return success(bookingData(booking));
    }

    private JSONObject listBookings(RealtimeCallContext context) {
        if (state.customer(context.callId()) == null) {
            return error("CUSTOMER_NOT_REGISTERED", "No hay un cliente identificado dentro de esta simulación.");
        }
        JSONArray out = new JSONArray();
        for (var booking : state.bookings(context.callId())) {
            if (booking.status() != BookingStatus.CONFIRMED || !booking.startAt().isAfter(Instant.now())) continue;
            out.put(bookingData(booking));
        }
        return success(new JSONObject().put("bookings", out));
    }

    private JSONObject rescheduleBooking(RealtimeCallContext context, JSONObject args) {
        UUID bookingId = uuid(required(args, "bookingId"));
        var booking = state.booking(context.callId(), bookingId);
        if (booking == null) return error("BOOKING_NOT_FOUND", "No encuentro esa reserva dentro de esta simulación.");
        if (booking.status() == BookingStatus.CANCELLED) {
            return error("BOOKING_CANCELLED", "Esa reserva de prueba ya está cancelada.");
        }

        String newStartRaw = required(args, "newStartAt");
        JSONObject availabilityArgs = new JSONObject()
                .put("serviceId", booking.serviceId().toString())
                .put("startAt", newStartRaw);
        String realRaw = realTools.execute(context, "check_booking_availability", availabilityArgs.toString());
        JSONObject availability = parse(realRaw);
        if (!availability.optBoolean("success", false)) return availability;
        JSONObject data = availability.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario no está disponible.");
        }
        Instant startAt = instant(data.optString("startAt", newStartRaw));
        Instant endAt = instant(data.optString("endAt", null));
        if (state.overlaps(context.callId(), booking.serviceId(), startAt, endAt, bookingId)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario ya está ocupado dentro de esta simulación.");
        }
        String localStart = data.optString("localStart", startAt.toString());
        var updated = state.rescheduleBooking(context.callId(), bookingId, startAt, endAt, localStart);
        return success(bookingData(updated));
    }

    private JSONObject cancelBooking(RealtimeCallContext context, JSONObject args) {
        UUID bookingId = uuid(required(args, "bookingId"));
        var booking = state.cancelBooking(context.callId(), bookingId);
        if (booking == null) return error("BOOKING_NOT_FOUND", "No encuentro esa reserva dentro de esta simulación.");
        return success(bookingData(booking));
    }

    private JSONObject createRequest(RealtimeCallContext context, JSONObject args) {
        String type = required(args, "requestType");
        String title = required(args, "title");
        RequestPriority priority = priority(optional(args, "priority"));
        var request = state.createRequest(context.callId(), type, title, priority);
        return success(new JSONObject()
                .put("requestId", request.id().toString())
                .put("status", "OPEN")
                .put("requestType", request.type())
                .put("title", request.title())
                .put("priority", request.priority().name()));
    }

    private JSONObject recordQuestion(RealtimeCallContext context, JSONObject args) {
        String question = required(args, "question");
        var item = state.recordQuestion(context.callId(), question);
        return success(new JSONObject()
                .put("questionId", item.id().toString())
                .put("question", item.question())
                .put("occurrences", 1)
                .put("status", "SIMULATED"));
    }

    private String traced(RealtimeCallContext context, String toolName, JSONObject result) {
        try {
            trace.recordTool(context.businessId(), context.callId(), toolName, result);
        } catch (Exception ignored) {
            // Trace failures must never turn a safe simulation into a real mutation.
        }
        return result.toString();
    }

    private static JSONObject bookingData(SimulatorStateService.SimulatedBooking booking) {
        return new JSONObject()
                .put("bookingId", booking.id().toString())
                .put("status", booking.status().name())
                .put("serviceId", booking.serviceId().toString())
                .put("service", booking.serviceName())
                .put("startAt", booking.startAt().toString())
                .put("endAt", booking.endAt().toString())
                .put("localStart", booking.localStart());
    }

    private static JSONObject parse(String raw) {
        try { return new JSONObject(raw); }
        catch (Exception e) { return error("SIMULATOR_INVALID_TOOL_RESULT", "La simulación recibió una respuesta inválida del backend."); }
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static String required(JSONObject args, String key) {
        String value = args.optString(key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta el argumento " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) return null;
        String value = args.optString(key, null);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new IllegalArgumentException("UUID inválido."); }
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Fecha/hora inválida.");
        try { return Instant.parse(value); }
        catch (Exception first) {
            try { return OffsetDateTime.parse(value).toInstant(); }
            catch (Exception second) { throw new IllegalArgumentException("Fecha/hora inválida."); }
        }
    }

    private static RequestPriority priority(String value) {
        if (value == null || value.isBlank()) return RequestPriority.NORMAL;
        try { return RequestPriority.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new IllegalArgumentException("priority debe ser LOW, NORMAL, HIGH o URGENT."); }
    }
}
