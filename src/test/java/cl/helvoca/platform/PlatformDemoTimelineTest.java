package cl.helvoca.platform;

import cl.helvoca.call.*;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEventRepository;
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
        BusinessOperationEventRepository operationEvents = mock(BusinessOperationEventRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.markReady();
        session.markActive();
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

        PlatformDemoTimelineResponse result = new PlatformDemoTimelineService(
                properties, sessions, calls, conversations, operations, operationEvents, summaries, actions)
                .timeline(sessionId);

        assertEquals(sessionId, result.sessionId());
        assertEquals(runtimeId, result.runtimeBusinessId());
        assertEquals(DemoSessionState.ACTIVE, result.sessionStatus());
        assertEquals("RECORDED_VALUE", result.proofOfValue().state());
        assertEquals(1, result.proofOfValue().calls());
        assertEquals(1, result.proofOfValue().operations());
        assertTrue(result.proofOfValue().facts().stream()
                .anyMatch(value -> value.contains("ORDER_CREATED")));
    }

    @Test
    void timelineHandlesNullAndFailureVariantsWithoutInventingFacts() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessOperationEventRepository operationEvents = mock(BusinessOperationEventRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.markReady();
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(session));

        CallSession call = new CallSession();
        ReflectionTestUtils.setField(call, "id", callId);
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        call.setStartedAt(Instant.parse("2026-10-01T06:30:00Z"));
        call.setResolution(" ");
        when(calls.findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of(call));

        CallSummary summary = new CallSummary();
        summary.setCallId(callId);
        summary.setSummary(" ");
        summary.setOutcome(" ");
        when(summaries.findByCallId(callId)).thenReturn(Optional.of(summary));

        CallAction failed = new CallAction();
        ReflectionTestUtils.setField(failed, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(failed, "createdAt", Instant.parse("2026-10-01T06:31:00Z"));
        failed.setBusinessId(runtimeId);
        failed.setCallId(callId);
        failed.setActionType(" ");
        failed.setSuccess(false);
        failed.setDetail(" ");
        failed.setErrorCode("ERR_DEMO");
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(failed));

        cl.helvoca.messaging.MessagingConversation conversation =
                new cl.helvoca.messaging.MessagingConversation();
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        conversation.setBusinessId(runtimeId);
        conversation.setOpenedAt(Instant.parse("2026-10-01T06:30:30Z"));
        conversation.setChannel(null);
        when(conversations.findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of(conversation));

        BusinessOperation operation = new BusinessOperation();
        operation.setId(UUID.randomUUID());
        operation.setBusinessId(runtimeId);
        operation.setDemoSessionId(sessionId);
        ReflectionTestUtils.setField(operation, "createdAt", Instant.parse("2026-10-01T06:32:00Z"));
        operation.setType(null);
        operation.setStatus(null);
        when(operations.findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId))
                .thenReturn(List.of(operation));

        cl.helvoca.operations.BusinessOperationEvent event =
                mock(cl.helvoca.operations.BusinessOperationEvent.class);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(event.getCreatedAt()).thenReturn(Instant.parse("2026-10-01T06:32:30Z"));
        when(event.getStatus()).thenReturn(null);
        when(event.getEventType()).thenReturn(" ");
        when(operationEvents.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(runtimeId, operation.getId()))
                .thenReturn(List.of(event));

        PlatformDemoTimelineResponse result = new PlatformDemoTimelineService(
                properties, sessions, calls, conversations, operations, operationEvents, summaries, actions)
                .timeline(sessionId);

        assertEquals("REVIEW_REQUIRED", result.proofOfValue().state());
        assertTrue(result.proofOfValue().facts().isEmpty());
        assertTrue(result.events().stream().anyMatch(e -> "Recorded call".equals(e.detail())));
        assertTrue(result.events().stream().anyMatch(e -> "Persisted call summary".equals(e.detail())));
        assertTrue(result.events().stream().anyMatch(e -> e.detail().contains("ERR_DEMO")));
        assertTrue(result.events().stream().anyMatch(e -> "Persisted operation event".equals(e.detail())));
    }

    @Test
    void timelineThrowsWhenRuntimeOrSessionIsMissing() {
        DemoRuntimeProperties missing = new DemoRuntimeProperties();
        PlatformDemoTimelineService noRuntime = new PlatformDemoTimelineService(
                missing,
                mock(DemoSessionRepository.class),
                mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class),
                mock(BusinessOperationRepository.class),
                mock(BusinessOperationEventRepository.class),
                mock(CallSummaryRepository.class),
                mock(CallActionRepository.class));
        assertThrows(IllegalStateException.class, () -> noRuntime.timeline(UUID.randomUUID()));

        UUID runtimeId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        when(sessions.findByIdAndRuntimeBusinessId(any(), eq(runtimeId))).thenReturn(Optional.empty());

        PlatformDemoTimelineService missingSession = new PlatformDemoTimelineService(
                properties, sessions,
                mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class),
                mock(BusinessOperationRepository.class),
                mock(BusinessOperationEventRepository.class),
                mock(CallSummaryRepository.class),
                mock(CallActionRepository.class));
        assertThrows(cl.helvoca.common.NotFoundException.class,
                () -> missingSession.timeline(UUID.randomUUID()));
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
        BusinessOperationEventRepository operationEvents = mock(BusinessOperationEventRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.markReady();
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(session));
        when(calls.findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId)).thenReturn(List.of());
        when(conversations.findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId)).thenReturn(List.of());
        when(operations.findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId)).thenReturn(List.of());

        PlatformDemoTimelineResponse result = new PlatformDemoTimelineService(
                properties, sessions, calls, conversations, operations, operationEvents, summaries, actions)
                .timeline(sessionId);

        assertEquals("REVIEW_REQUIRED", result.proofOfValue().state());
        assertFalse(result.proofOfValue().followUps().isEmpty());
        assertTrue(result.proofOfValue().facts().isEmpty());
    }
}
