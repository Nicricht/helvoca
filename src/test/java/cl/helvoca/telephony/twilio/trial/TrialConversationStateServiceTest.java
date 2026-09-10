package cl.helvoca.telephony.twilio.trial;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrialConversationStateServiceTest {
    @Test
    void remembersStructuredBookingDataAcrossTurns() {
        UUID callId = UUID.randomUUID();
        TrialConversationStateService state = new TrialConversationStateService();

        assertTrue(state.beginInitialization(callId));
        assertFalse(state.beginInitialization(callId));

        JSONObject service = new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", "Reserva de mesa");
        state.observeToolResult(callId, "list_services", success(
                new JSONObject().put("services", new JSONArray().put(service))).toString());
        state.observeToolResult(callId, "find_caller", success(
                new JSONObject().put("found", false)).toString());

        String beforeRegistration = state.promptContext(callId);
        assertTrue(beforeRegistration.contains("Reserva de mesa"));
        assertTrue(beforeRegistration.contains("cliente asociado al teléfono: no"));
        assertEquals("Claro. ¿A nombre de quién sería?", state.timeoutFallback(callId, "Quiero reservar"));

        state.observeToolResult(callId, "register_caller", success(
                new JSONObject().put("name", "Nicolás")).toString());
        state.observeToolResult(callId, "check_booking_availability", success(
                new JSONObject()
                        .put("serviceId", service.getString("id"))
                        .put("serviceName", "Reserva de mesa")
                        .put("startAt", "2026-09-11T21:00:00Z")
                        .put("available", true)).toString());

        String after = state.promptContext(callId);
        assertTrue(after.contains("Nicolás"));
        assertTrue(after.contains("2026-09-11T21:00:00Z"));
        assertTrue(after.contains("disponibilidad comprobada: true"));
        assertTrue(state.afterRegistrationPrompt(callId).contains("horario que indicaste"));
    }

    @Test
    void remembersBookingIdWhenThereIsExactlyOneUpcomingBooking() {
        UUID callId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        TrialConversationStateService state = new TrialConversationStateService();

        JSONObject booking = new JSONObject()
                .put("bookingId", bookingId.toString())
                .put("serviceId", serviceId.toString())
                .put("service", "Reserva de mesa")
                .put("startAt", "2026-09-12T18:00:00Z")
                .put("status", "CONFIRMED");

        state.observeToolResult(callId, "list_customer_bookings", success(
                new JSONObject().put("bookings", new JSONArray().put(booking))).toString());

        String prompt = state.promptContext(callId);
        assertTrue(prompt.contains("reservas futuras encontradas: 1"));
        assertTrue(prompt.contains("bookingId seleccionado: " + bookingId));
        assertTrue(prompt.contains("serviceId seleccionado: " + serviceId));
        assertTrue(prompt.contains("reserva confirmada: true"));
    }

    @Test
    void doesNotAutoSelectBookingWhenSeveralUpcomingBookingsExist() {
        UUID callId = UUID.randomUUID();
        TrialConversationStateService state = new TrialConversationStateService();

        JSONArray bookings = new JSONArray()
                .put(new JSONObject()
                        .put("bookingId", UUID.randomUUID().toString())
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("service", "Reserva de mesa")
                        .put("startAt", "2026-09-12T18:00:00Z")
                        .put("status", "CONFIRMED"))
                .put(new JSONObject()
                        .put("bookingId", UUID.randomUUID().toString())
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("service", "Reserva de mesa")
                        .put("startAt", "2026-09-13T18:00:00Z")
                        .put("status", "CONFIRMED"));

        state.observeToolResult(callId, "list_customer_bookings", success(
                new JSONObject().put("bookings", bookings)).toString());

        String prompt = state.promptContext(callId);
        assertTrue(prompt.contains("reservas futuras encontradas: 2"));
        assertFalse(prompt.contains("bookingId seleccionado:"));
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }
}
