package cl.helvoca.call;

import cl.helvoca.request.RequestCreationObservationDispatcher;
import cl.helvoca.request.RequestSource;
import org.json.JSONObject;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallTraceServiceTest {

    @Test
    void callResponseExposesExactAiModelUsedByTheCall() {
        CallSession call = new CallSession();
        call.setAiProvider("gemini");
        call.setAiModel("models/gemini-live-2.5-flash-native-audio");

        CallResponse response = CallResponse.from(call);

        assertEquals("gemini", response.aiProvider());
        assertEquals("models/gemini-live-2.5-flash-native-audio", response.aiModel());
    }

    @Test
    void callActionResponseExposesMeasuredToolLatency() {
        CallAction action = new CallAction();
        action.setActionType("AVAILABILITY_CHECKED");
        action.setSuccess(true);
        action.setDurationMs(143L);

        CallActionResponse response = CallActionResponse.from(action);

        assertEquals("AVAILABILITY_CHECKED", response.actionType());
        assertTrue(response.success());
        assertEquals(143L, response.durationMs());
    }

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

        service.recordTool(businessId, callId, "create_booking", result, 87L);

        assertEquals("BOOKING_CREATED", call.getResolution());
        ArgumentCaptor<CallAction> captor = ArgumentCaptor.forClass(CallAction.class);
        verify(actions).save(captor.capture());
        CallAction action = captor.getValue();
        assertTrue(action.isSuccess());
        assertEquals("BOOKING_CREATED", action.getActionType());
        assertEquals("BOOKING", action.getEntityType());
        assertEquals(bookingId, action.getEntityId());
        assertTrue(action.getDetail().contains("Consulta"));
        assertEquals(87L, action.getDurationMs());
    }

    @Test
    void bookingProposalDoesNotPretendThatBookingAlreadyExists() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTraceService service = new CallTraceService(calls, actions);
        JSONObject result = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .put("requiresConfirmation", true)
                        .put("bookingCreated", false)
                        .put("service", "Consulta")
                        .put("localStart", "2026-09-28T10:00:00-03:00"))
                .put("error", JSONObject.NULL);

        service.recordTool(businessId, callId, "create_booking", result);

        assertNull(call.getResolution(), "A proposal must not resolve the call as BOOKING_CREATED");
        ArgumentCaptor<CallAction> captor = ArgumentCaptor.forClass(CallAction.class);
        verify(actions).save(captor.capture());
        CallAction action = captor.getValue();
        assertTrue(action.isSuccess());
        assertEquals("BOOKING_PROPOSED", action.getActionType());
        assertNull(action.getEntityId());
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
    @Test
    void realSuccessfulRequestToolResultSchedulesCommitBoundVoiceObservation() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        RequestCreationObservationDispatcher dispatcher = mock(RequestCreationObservationDispatcher.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(new CallSession()));

        CallTraceService service = new CallTraceService(calls, actions);
        ReflectionTestUtils.setField(service, "requestObservations", dispatcher);
        JSONObject result = new JSONObject().put("success", true).put("data",
                new JSONObject().put("requestId", requestId.toString())
                        .put("operationId", operationId.toString()));

        service.recordTool(businessId, callId, "create_request", result);
        verify(dispatcher).afterSuccessfulCommit(
                businessId, callId, RequestSource.AI_CALL, requestId, operationId);
        verify(actions).save(any(CallAction.class));
    }

    @Test
    void failedRequestToolResultNeverSchedulesObservation() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        RequestCreationObservationDispatcher dispatcher = mock(RequestCreationObservationDispatcher.class);
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        when(calls.findByIdAndBusinessId(callId, businessId))
                .thenReturn(Optional.of(new CallSession()));

        CallTraceService service = new CallTraceService(calls, actions);
        ReflectionTestUtils.setField(service, "requestObservations", dispatcher);
        JSONObject failed = new JSONObject().put("success", false)
                .put("error", new JSONObject().put("code", "REQUEST_OPERATION_FAILED"));
        service.recordTool(businessId, callId, "create_request", failed);
        verifyNoInteractions(dispatcher);
    }

}
