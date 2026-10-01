package cl.helvoca.platform;

import cl.helvoca.call.*;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDemoTimelineTest {

    @Test
    void timelineIsScopedBySessionAndRuntimeAndProofUsesOnlyPersistedFacts() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        cl.helvoca.operations.BusinessOperationEventRepository operationEvents = mock(cl.helvoca.operations.BusinessOperationEventRepository.class);
        cl.helvoca.operations.BusinessOperationEventRepository operationEvents = mock(cl.helvoca.operations.BusinessOperationEventRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);

        DemoSession session = new DemoSession();
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.setRuntimeBusinessId(runtimeId);
        session.setStatus(DemoSessionState.ACTIVE);
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(session));

        CallSession call = new CallSession();
        ReflectionTestUtils.setField(call, "id", callId);
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        call.setStatus(CallStatus.COMPLETED);
        call.setResolution("ORDER_CREATED");
        call.setStartedAt(Instant.parse("2026-10-01T06:30:00Z"));
        when(calls.findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of(call));

        CallSummary summary = new CallSummary();
        summary.setCallId(callId);
        summary.setSummary("Cliente pidió un producto y el sistema registró la solicitud.");
        summary.setOutcome("ORDER_CREATED");
        when(summaries.findByCallId(callId)).thenReturn(Optional.of(summary));

        CallAction action = new CallAction();
        ReflectionTestUtils.setField(action, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(action, "createdAt", Instant.parse("2026-10-01T06:31:00Z"));
        action.setBusinessId(runtimeId);
        action.setCallId(callId);
        action.setActionType("ORDER_CREATED");
        action.setSuccess(true);
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(action));

        BusinessOperation operation = new BusinessOperation();
        operation.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(operation, "createdAt", Instant.parse("2026-10-01T06:31:30Z"));
        operation.setBusinessId(runtimeId);
        operation.setDemoSessionId(sessionId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        when(operations.findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of(operation));
        when(operationEvents.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(runtimeId, operation.getId()))
                .thenReturn(List.of());
        when(conversations.findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of());

        PlatformDemoTimelineService service = new PlatformDemoTimelineService(
                properties, sessions, calls, conversations, operations, operationEvents, summaries, actions);

        PlatformDemoTimelineResponse result = service.timeline(sessionId);

        assertEquals(sessionId, result.sessionId());
        assertEquals(runtimeId, result.runtimeBusinessId());
        assertEquals("RECORDED_VALUE", result.proofOfValue().state());
        assertEquals(1, result.proofOfValue().calls());
        assertEquals(1, result.proofOfValue().operations());
        assertTrue(result.proofOfValue().facts().stream()
                .anyMatch(value -> value.contains("ORDER_CREATED")));
        verify(calls).findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId);
        verify(operations).findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId);
        verify(conversations).findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId);
    }

    @Test
    void missingOutcomeEvidenceIsReportedForReviewInsteadOfInventedSuccess() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);

        DemoSession session = new DemoSession();
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.setRuntimeBusinessId(runtimeId);
        session.setStatus(DemoSessionState.ACTIVE);
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(session));
        when(calls.findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId)).thenReturn(List.of());
        when(conversations.findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId)).thenReturn(List.of());
        when(operations.findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId)).thenReturn(List.of());

        PlatformDemoTimelineResponse result = new PlatformDemoTimelineService(
                properties, sessions, calls, conversations, operations, operationEvents, summaries, actions).timeline(sessionId);

        assertEquals("REVIEW_REQUIRED", result.proofOfValue().state());
        assertFalse(result.proofOfValue().followUps().isEmpty());
        assertTrue(result.proofOfValue().facts().isEmpty());
    }
}
