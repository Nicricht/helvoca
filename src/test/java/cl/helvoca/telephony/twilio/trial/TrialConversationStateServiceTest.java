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

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }
}
