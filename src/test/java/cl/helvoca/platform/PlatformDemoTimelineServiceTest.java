package cl.helvoca.platform;

import cl.helvoca.call.*;
import cl.helvoca.common.NotFoundException;
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

class PlatformDemoTimelineServiceTest {

    @Test
    void timelineCombinesOnlyPersistedEvidenceFromCorrelatedCalls() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        CallSummaryRepository summaries = mock(CallSummaryRepository.class);

        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", sessionId);
        session.markStaged();
        session.markReady();
        session.markActive();
        session.markFinished();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        Instant started = Instant.parse("2026-10-01T07:30:00Z");
        CallSession call = new CallSession();
        ReflectionTestUtils.setField(call, "id", callId);
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        call.setDirection(CallDirection.INBOUND);
        call.setTelephonyProvider("twilio");
        call.setAiProvider("gemini");
        call.setCallerNumber("+56911111111");
        call.setDestinationNumber("+14355550000");
        call.setProviderCallId("CA-live-demo");
        call.setStatus(CallStatus.COMPLETED);
        call.setStartedAt(started);
        call.setAnsweredAt(started.plusSeconds(1));
        call.setEndedAt(started.plusSeconds(45));
        call.setDurationSeconds(45);
        when(calls.findAllByDemoSessionIdOrderByStartedAtAsc(sessionId)).thenReturn(List.of(call));

        CallTranscript user = transcript(callId, "USER", "Quiero dos sakes", 1, started.plusSeconds(5));
        CallTranscript assistant = transcript(callId, "ASSISTANT", "Claro, son dos.", 2, started.plusSeconds(7));
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(user, assistant));

        CallAction action = new CallAction();
        ReflectionTestUtils.setField(action, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(action, "createdAt", started.plusSeconds(10));
        action.setBusinessId(runtimeId);
        action.setCallId(callId);
        action.setActionType("ORDER_CREATED");
        action.setSuccess(true);
        action.setEntityType("ORDER");
        action.setEntityId(UUID.randomUUID());
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(action));

        BusinessOperation operation = new BusinessOperation();
        operation.setId(UUID.randomUUID());
        operation.setBusinessId(runtimeId);
        operation.setSourceReferenceId(callId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        ReflectionTestUtils.setField(operation, "createdAt", started.plusSeconds(12));
        ReflectionTestUtils.setField(operation, "updatedAt", started.plusSeconds(13));
        when(operations.findAllByBusinessIdAndSourceReferenceIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(operation));

        CallSummary summary = new CallSummary();
        ReflectionTestUtils.setField(summary, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(summary, "createdAt", started.plusSeconds(46));
        summary.setCallId(callId);
        summary.setSummary("Cliente confirmó dos productos.");
        summary.setIntent("ORDER");
        summary.setOutcome("CONFIRMED");
        when(summaries.findByCallId(callId)).thenReturn(Optional.of(summary));

        PlatformDemoTimelineResponse response = new PlatformDemoTimelineService(
                properties, sessions, calls, transcripts, actions, operations, summaries)
                .timeline(sessionId);

        assertEquals(sessionId, response.sessionId());
        assertEquals(DemoSessionState.FINISHED, response.state());
        assertEquals(1, response.callCount());
        assertTrue(response.items().stream().anyMatch(item -> "CALL_STARTED".equals(item.kind())));
        assertTrue(response.items().stream().anyMatch(item -> "CALL_ANSWERED".equals(item.kind())));
        assertEquals(2, response.items().stream().filter(item -> "TRANSCRIPT".equals(item.kind())).count());
        assertTrue(response.items().stream().anyMatch(item -> "ACTION".equals(item.kind())
                && "SUCCESS".equals(item.status())));
        assertTrue(response.items().stream().anyMatch(item -> "OPERATION".equals(item.kind())
                && "CONFIRMED".equals(item.status())));
        assertTrue(response.items().stream().anyMatch(item -> "CALL_FINISHED".equals(item.kind())
                && "COMPLETED".equals(item.status())));
        assertTrue(response.items().stream().anyMatch(item -> "SUMMARY".equals(item.kind())));
    }

    @Test
    void foreignRuntimeSessionIsHiddenEvenFromTimelineLookup() {
        UUID configuredRuntime = UUID.randomUUID();
        UUID foreignRuntime = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(configuredRuntime.toString());
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoSession foreign = DemoSession.preparing(UUID.randomUUID(), foreignRuntime, "rev");
        ReflectionTestUtils.setField(foreign, "id", sessionId);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(foreign));

        PlatformDemoTimelineService service = new PlatformDemoTimelineService(
                properties,
                sessions,
                mock(CallSessionRepository.class),
                mock(CallTranscriptRepository.class),
                mock(CallActionRepository.class),
                mock(BusinessOperationRepository.class),
                mock(CallSummaryRepository.class));

        assertThrows(NotFoundException.class, () -> service.timeline(sessionId));
    }

    private static CallTranscript transcript(
            UUID callId, String speaker, String content, int sequence, Instant createdAt) {
        CallTranscript item = new CallTranscript();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(item, "createdAt", createdAt);
        item.setCallId(callId);
        item.setSpeaker(speaker);
        item.setContent(content);
        item.setSequenceNumber(sequence);
        return item;
    }
}
