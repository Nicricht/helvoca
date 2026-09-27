package cl.helvoca.ai.realtime;

import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationStateService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingConversationStateMachineTest {
    @Mock ConversationStateService conversationState;

    @Test
    void knownCallerClosesIdentityFieldsInsteadOfAskingThemAgain() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", "MZ-state");

        when(conversationState.apply(
                eq(businessId), eq(callId), eq(BusinessOrder.Source.VOICE), isNull(), anyMap()))
                .thenAnswer(invocation -> state(invocation.getArgument(4), 2));

        JSONObject result = new BookingConversationStateMachine(conversationState).decorate(
                context,
                "find_caller",
                new JSONObject(),
                success(new JSONObject()
                        .put("found", true)
                        .put("name", "Nicolás")
                        .put("phone", "+56911111111")));

        JSONObject machine = result.getJSONObject("conversationState");
        assertEquals("ASK_SERVICE", machine.getString("nextAction"));
        assertEquals("SERVICE", machine.getString("nextRequiredField"));
        assertTrue(machine.getJSONArray("closedFields").toList().contains("name"));
        assertTrue(machine.getJSONArray("closedFields").toList().contains("phone"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> patch = ArgumentCaptor.forClass(Map.class);
        verify(conversationState).apply(
                eq(businessId), eq(callId), eq(BusinessOrder.Source.VOICE), isNull(), patch.capture());
        assertEquals(true, patch.getValue().get("bookingCustomerKnown"));
        assertEquals("Nicolás", patch.getValue().get("bookingCustomerName"));
    }

    @Test
    void resolvedServiceSlotAndIdentityGoStraightToProposal() {
        ConversationOperationState state = state(new LinkedHashMap<>(Map.of(
                "serviceId", UUID.randomUUID().toString(),
                "serviceName", "Consulta",
                "startAt", "2026-09-28T13:30:00Z",
                "localStart", "2026-09-28T10:30:00-03:00",
                "bookingSlotValidated", true,
                "bookingCustomerKnown", true,
                "bookingCustomerName", "Nicolás",
                "bookingCustomerPhone", "+56911111111",
                "confirmationPending", false,
                "bookingFlowStage", "SLOT_VALIDATED"
        )), 5);

        JSONObject machine = BookingConversationStateMachine.snapshot(state);

        assertEquals("NONE", machine.getString("nextRequiredField"));
        assertEquals("CREATE_BOOKING_PROPOSAL", machine.getString("nextAction"));
        assertEquals(
                java.util.List.of("service", "date", "time", "name", "phone"),
                machine.getJSONArray("closedFields").toList());
    }

    @Test
    void proposalRequiresExactlyOneConfirmationWithoutReaskingResolvedFields() {
        ConversationOperationState state = state(new LinkedHashMap<>(Map.of(
                "serviceId", UUID.randomUUID().toString(),
                "serviceName", "Consulta",
                "startAt", "2026-09-28T13:30:00Z",
                "bookingSlotValidated", true,
                "bookingCustomerKnown", true,
                "confirmationPending", true,
                "bookingFlowStage", "WAITING_CONFIRMATION",
                "operationId", UUID.randomUUID().toString()
        )), 6);

        JSONObject machine = BookingConversationStateMachine.snapshot(state);

        assertEquals("CONFIRMATION", machine.getString("nextRequiredField"));
        assertEquals("ASK_CONFIRMATION_ONCE", machine.getString("nextAction"));
        assertTrue(machine.getJSONArray("closedFields").toList().containsAll(
                java.util.List.of("service", "date", "time", "name", "phone")));
        assertFalse(machine.getJSONArray("closedFields").toList().contains("confirmation"));
    }

    @Test
    void confirmedBookingIsTerminalAndMustNotBeConfirmedAgain() {
        ConversationOperationState state = state(new LinkedHashMap<>(Map.of(
                "serviceId", UUID.randomUUID().toString(),
                "startAt", "2026-09-28T13:30:00Z",
                "bookingSlotValidated", true,
                "bookingCustomerKnown", true,
                "confirmationPending", false,
                "bookingFlowStage", "CONFIRMED",
                "bookingId", UUID.randomUUID().toString()
        )), 7);

        JSONObject machine = BookingConversationStateMachine.snapshot(state);

        assertEquals("NONE", machine.getString("nextRequiredField"));
        assertEquals("BOOKING_COMPLETE", machine.getString("nextAction"));
        assertTrue(machine.getBoolean("bookingConfirmed"));
        assertTrue(machine.getJSONArray("closedFields").toList().contains("confirmation"));
    }

    @Test
    void browsingAnotherDateReopensOnlyTheSlotAndInvalidatesPendingConfirmation() {
        JSONObject data = new JSONObject()
                .put("serviceId", UUID.randomUUID().toString())
                .put("serviceName", "Consulta")
                .put("date", "2026-09-29");

        LinkedHashMap<String, Object> patch = BookingConversationStateMachine.patchFor(
                "list_available_slots",
                new JSONObject().put("date", "2026-09-29"),
                data);

        assertEquals("SELECTING_SLOT", patch.get("bookingFlowStage"));
        assertEquals(false, patch.get("bookingSlotValidated"));
        assertEquals(false, patch.get("confirmationPending"));
        assertTrue(patch.containsKey("startAt"));
        assertNull(patch.get("startAt"));
        assertTrue(patch.containsKey("localStart"));
        assertNull(patch.get("localStart"));
    }

    private static ConversationOperationState state(Map<String, Object> values, int revision) {
        ConversationOperationState state = new ConversationOperationState();
        state.setBusinessId(UUID.randomUUID());
        state.setSourceReferenceId(UUID.randomUUID());
        state.setChannel(BusinessOrder.Source.VOICE);
        state.setRevision(revision);
        state.setState(values);
        return state;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL);
    }
}
