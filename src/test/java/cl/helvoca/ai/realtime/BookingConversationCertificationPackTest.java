package cl.helvoca.ai.realtime;

import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BookingConversationCertificationPackTest {

    record Scenario(String name,
                    Map<String, Object> state,
                    String nextRequiredField,
                    String nextAction,
                    List<String> closedFields) {}

    @TestFactory
    Stream<DynamicTest> bookingProgressionNeverReasksClosedFields() {
        String serviceId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();

        List<Scenario> scenarios = List.of(
                new Scenario(
                        "service known asks only date/time",
                        map(
                                "intent", "BOOKING",
                                "serviceId", serviceId,
                                "serviceName", "Consulta",
                                "bookingSlotValidated", false,
                                "bookingCustomerKnown", false,
                                "confirmationPending", false),
                        "DATE_TIME",
                        "ASK_OR_VALIDATE_DATE_TIME",
                        List.of("service")),
                new Scenario(
                        "validated slot asks only identity",
                        map(
                                "intent", "BOOKING",
                                "serviceId", serviceId,
                                "startAt", "2026-09-28T13:30:00Z",
                                "bookingSlotValidated", true,
                                "bookingCustomerKnown", false,
                                "confirmationPending", false),
                        "CUSTOMER_IDENTITY",
                        "IDENTIFY_CUSTOMER",
                        List.of("service", "date", "time")),
                new Scenario(
                        "all required data goes straight to proposal",
                        map(
                                "intent", "BOOKING",
                                "serviceId", serviceId,
                                "startAt", "2026-09-28T13:30:00Z",
                                "bookingSlotValidated", true,
                                "bookingCustomerKnown", true,
                                "bookingCustomerName", "Nicolás",
                                "bookingCustomerPhone", "+56911111111",
                                "confirmationPending", false),
                        "NONE",
                        "CREATE_BOOKING_PROPOSAL",
                        List.of("service", "date", "time", "name", "phone")),
                new Scenario(
                        "proposal asks confirmation exactly once",
                        map(
                                "intent", "BOOKING",
                                "serviceId", serviceId,
                                "startAt", "2026-09-28T13:30:00Z",
                                "bookingSlotValidated", true,
                                "bookingCustomerKnown", true,
                                "confirmationPending", true,
                                "bookingFlowStage", "WAITING_CONFIRMATION",
                                "operationId", operationId),
                        "CONFIRMATION",
                        "ASK_CONFIRMATION_ONCE",
                        List.of("service", "date", "time", "name", "phone")),
                new Scenario(
                        "confirmed booking is terminal",
                        map(
                                "intent", "BOOKING",
                                "serviceId", serviceId,
                                "startAt", "2026-09-28T13:30:00Z",
                                "bookingSlotValidated", true,
                                "bookingCustomerKnown", true,
                                "confirmationPending", false,
                                "bookingFlowStage", "CONFIRMED",
                                "bookingId", bookingId),
                        "NONE",
                        "BOOKING_COMPLETE",
                        List.of("service", "date", "time", "name", "phone", "confirmation"))
        );

        return scenarios.stream().map(s -> DynamicTest.dynamicTest(s.name(), () -> {
            JSONObject snapshot = BookingConversationStateMachine.snapshot(state(s.state()));
            assertEquals(s.nextRequiredField(), snapshot.getString("nextRequiredField"));
            assertEquals(s.nextAction(), snapshot.getString("nextAction"));
            List<Object> actualClosed = snapshot.getJSONArray("closedFields").toList();
            for (String field : s.closedFields()) {
                assertTrue(actualClosed.contains(field),
                        () -> "Expected closed field '" + field + "' in " + actualClosed);
            }
        }));
    }

    @Test
    void callerLookupAloneNeverForcesBookingIntent() {
        JSONObject snapshot = BookingConversationStateMachine.snapshot(state(map(
                "bookingCustomerKnown", true,
                "bookingCustomerName", "Nicolás",
                "bookingCustomerPhone", "+56911111111"
        )));

        assertEquals("NONE", snapshot.getString("nextRequiredField"));
        assertEquals("CONTINUE_CURRENT_INTENT", snapshot.getString("nextAction"));
        assertTrue(snapshot.getJSONArray("closedFields").toList().contains("name"));
        assertTrue(snapshot.getJSONArray("closedFields").toList().contains("phone"));
    }

    @Test
    void changingDateReopensOnlySlotSelectionAndCancelsPendingConfirmation() {
        LinkedHashMap<String, Object> patch = BookingConversationStateMachine.patchFor(
                "list_available_slots",
                new JSONObject().put("date", "2026-09-29"),
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("serviceName", "Consulta")
                        .put("date", "2026-09-29"));

        assertEquals("BOOKING", patch.get("intent"));
        assertEquals("SELECTING_SLOT", patch.get("bookingFlowStage"));
        assertEquals(false, patch.get("bookingSlotValidated"));
        assertEquals(false, patch.get("confirmationPending"));
        assertTrue(patch.containsKey("startAt"));
        assertNull(patch.get("startAt"));
        assertTrue(patch.containsKey("localStart"));
        assertNull(patch.get("localStart"));
    }

    @Test
    void unavailableSlotCannotRemainValidated() {
        LinkedHashMap<String, Object> patch = BookingConversationStateMachine.patchFor(
                "check_booking_availability",
                new JSONObject(),
                new JSONObject()
                        .put("available", false)
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("serviceName", "Consulta"));

        assertEquals(false, patch.get("bookingSlotValidated"));
        assertEquals("SELECTING_SLOT", patch.get("bookingFlowStage"));
        assertNull(patch.get("startAt"));
        assertEquals(false, patch.get("confirmationPending"));
    }

    @Test
    void proposalWithoutBookingIdIsNeverCertifiedAsConfirmed() {
        LinkedHashMap<String, Object> patch = BookingConversationStateMachine.patchFor(
                "create_booking",
                new JSONObject(),
                new JSONObject()
                        .put("success", true)
                        .put("requiresConfirmation", true)
                        .put("operationId", UUID.randomUUID().toString())
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", "2026-09-28T13:30:00Z"));

        assertEquals("WAITING_CONFIRMATION", patch.get("bookingFlowStage"));
        assertEquals(true, patch.get("confirmationPending"));
        assertNull(patch.get("bookingId"));
    }

    @Test
    void bookingIdIsRequiredForTerminalBookingState() {
        String bookingId = UUID.randomUUID().toString();
        LinkedHashMap<String, Object> patch = BookingConversationStateMachine.patchFor(
                "create_booking",
                new JSONObject(),
                new JSONObject()
                        .put("success", true)
                        .put("bookingId", bookingId)
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", "2026-09-28T13:30:00Z")
                        .put("status", "CONFIRMED"));

        assertEquals("CONFIRMED", patch.get("bookingFlowStage"));
        assertEquals(false, patch.get("confirmationPending"));
        assertEquals(bookingId, patch.get("bookingId"));
    }

    private static ConversationOperationState state(Map<String, Object> values) {
        ConversationOperationState state = new ConversationOperationState();
        state.setBusinessId(UUID.randomUUID());
        state.setSourceReferenceId(UUID.randomUUID());
        state.setChannel(BusinessOrder.Source.VOICE);
        state.setRevision(1);
        state.setState(new LinkedHashMap<>(values));
        return state;
    }

    private static LinkedHashMap<String, Object> map(Object... pairs) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return out;
    }
}
