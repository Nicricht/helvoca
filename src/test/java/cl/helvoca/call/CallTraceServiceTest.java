package cl.helvoca.call;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallTraceServiceTest {

    @Test
    void successfulBookingPersistsLinkedActionAndResolution() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CallSession call = new CallSession();
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTraceService service = new CallTraceService(calls, actions);
        JSONObject result = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("service", "Consulta")
                        .put("localStart", "2026-09-12T10:00:00-03:00"))
                .put("error", JSONObject.NULL);

        service.recordTool(businessId, callId, "create_booking", result);

        assertEquals("BOOKING_CREATED", call.getResolution());
        ArgumentCaptor<CallAction> captor = ArgumentCaptor.forClass(CallAction.class);
        verify(actions).save(captor.capture());
        CallAction action = captor.getValue();
        assertTrue(action.isSuccess());
        assertEquals("BOOKING_CREATED", action.getActionType());
        assertEquals("BOOKING", action.getEntityType());
        assertEquals(bookingId, action.getEntityId());
        assertTrue(action.getDetail().contains("Consulta"));
    }

    @Test
    void failedToolDoesNotClaimSuccessfulResolution() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTraceService service = new CallTraceService(calls, actions);
        JSONObject result = new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", "BOOKING_SLOT_UNAVAILABLE"));

        service.recordTool(businessId, callId, "create_booking", result);

        assertNull(call.getResolution());
        ArgumentCaptor<CallAction> captor = ArgumentCaptor.forClass(CallAction.class);
        verify(actions).save(captor.capture());
        assertFalse(captor.getValue().isSuccess());
        assertEquals("BOOKING_SLOT_UNAVAILABLE", captor.getValue().getErrorCode());
    }

    @Test
    void informationalLookupCannotOverwriteConfirmedBusinessOutcome() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        call.setResolution("REQUEST_CREATED");
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTraceService service = new CallTraceService(calls, actions);
        service.recordTool(businessId, callId, "get_business_information",
                new JSONObject().put("success", true).put("data", new JSONObject()).put("error", JSONObject.NULL));

        assertEquals("REQUEST_CREATED", call.getResolution());
    }

    @Test
    void carrierAcceptanceIsRequiredForHumanTransferredOutcome() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTraceService service = new CallTraceService(calls, actions);
        service.recordHumanTransfer(businessId, callId, false, "+56911111111");
        assertNull(call.getResolution());

        service.recordHumanTransfer(businessId, callId, true, "+56911111111");
        assertEquals("HUMAN_TRANSFERRED", call.getResolution());
    }
}
