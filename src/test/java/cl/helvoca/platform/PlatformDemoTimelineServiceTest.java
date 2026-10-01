package cl.helvoca.platform;

import cl.helvoca.call.*;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

    @Test
    void timelineHandlesSparseFailedAndUntrustedEvidenceWithoutInventingDetails() {
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
        session.markReady();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        CallSession wrongBusiness = new CallSession();
        ReflectionTestUtils.setField(wrongBusiness, "id", UUID.randomUUID());
        wrongBusiness.setBusinessId(UUID.randomUUID());
        wrongBusiness.setDemoSessionId(sessionId);

        CallSession wrongSession = new CallSession();
        ReflectionTestUtils.setField(wrongSession, "id", UUID.randomUUID());
        wrongSession.setBusinessId(runtimeId);
        wrongSession.setDemoSessionId(UUID.randomUUID());

        CallSession call = new CallSession();
        ReflectionTestUtils.setField(call, "id", callId);
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        call.setDirection(CallDirection.INBOUND);
        call.setTelephonyProvider("twilio");
        call.setCallerNumber("1234");
        call.setStartedAt(null);
        call.setStatus(null);
        call.setAnsweredAt(Instant.parse("2026-10-01T08:00:01Z"));
        call.setAiProvider("gemini");
        call.setAiModel(null);
        call.setEndedAt(Instant.parse("2026-10-01T08:00:20Z"));
        call.setDurationSeconds(null);

        when(calls.findAllByDemoSessionIdOrderByStartedAtAsc(sessionId))
                .thenReturn(List.of(wrongBusiness, wrongSession, call));

        CallTranscript other = transcript(callId, "SYSTEM", "Contexto", 1,
                Instant.parse("2026-10-01T08:00:02Z"));
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId)).thenReturn(List.of(other));

        CallAction failed = new CallAction();
        ReflectionTestUtils.setField(failed, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(failed, "createdAt", Instant.parse("2026-10-01T08:00:03Z"));
        failed.setBusinessId(runtimeId);
        failed.setCallId(callId);
        failed.setActionType("LOOKUP");
        failed.setSuccess(false);
        failed.setEntityType(null);
        failed.setEntityId(null);

        CallAction explicit = new CallAction();
        ReflectionTestUtils.setField(explicit, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(explicit, "createdAt", Instant.parse("2026-10-01T08:00:04Z"));
        explicit.setBusinessId(runtimeId);
        explicit.setCallId(callId);
        explicit.setActionType("CHECK");
        explicit.setSuccess(true);
        explicit.setDetail("Persisted detail");
        explicit.setEntityType("REQUEST");
        explicit.setEntityId(null);
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(failed, explicit));

        BusinessOperation sparse = new BusinessOperation();
        sparse.setId(UUID.randomUUID());
        sparse.setBusinessId(runtimeId);
        sparse.setSourceReferenceId(callId);
        sparse.setType(null);
        sparse.setStatus(null);
        sparse.setTotal(null);
        ReflectionTestUtils.setField(sparse, "createdAt", Instant.parse("2026-10-01T08:00:05Z"));
        ReflectionTestUtils.setField(sparse, "updatedAt", null);

        BusinessOperation amount = new BusinessOperation();
        amount.setId(UUID.randomUUID());
        amount.setBusinessId(runtimeId);
        amount.setSourceReferenceId(callId);
        amount.setType(BusinessOperation.Type.QUOTE);
        amount.setStatus(BusinessOperation.Status.PROPOSED);
        amount.setTotal(new BigDecimal("2500.00"));
        amount.setCurrency(null);
        ReflectionTestUtils.setField(amount, "createdAt", Instant.parse("2026-10-01T08:00:06Z"));
        ReflectionTestUtils.setField(amount, "updatedAt", Instant.parse("2026-10-01T08:00:07Z"));
        when(operations.findAllByBusinessIdAndSourceReferenceIdOrderByCreatedAtAsc(runtimeId, callId))
                .thenReturn(List.of(sparse, amount));

        CallSummary summary = new CallSummary();
        ReflectionTestUtils.setField(summary, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(summary, "createdAt", Instant.parse("2026-10-01T08:00:21Z"));
        summary.setCallId(callId);
        summary.setSummary("Resumen persistido");
        summary.setIntent(" ");
        summary.setOutcome(null);
        when(summaries.findByCallId(callId)).thenReturn(Optional.of(summary));

        PlatformDemoTimelineResponse response = new PlatformDemoTimelineService(
                properties, sessions, calls, transcripts, actions, operations, summaries)
                .timeline(sessionId);

        assertEquals(1, response.callCount());
        assertFalse(response.items().stream().anyMatch(item -> "SESSION_PREPARED".equals(item.kind())));
        assertTrue(response.items().stream().anyMatch(item -> "CALL_STARTED".equals(item.kind())
                && "UNKNOWN".equals(item.status())
                && item.detail().contains("••••")));
        assertTrue(response.items().stream().anyMatch(item -> "CALL_ANSWERED".equals(item.kind())
                && item.detail().contains("gemini")));
        assertTrue(response.items().stream().anyMatch(item -> "TRANSCRIPT".equals(item.kind())
                && "SYSTEM".equals(item.title())));
        assertTrue(response.items().stream().anyMatch(item -> "ACTION".equals(item.kind())
                && "FAILED".equals(item.status())
                && item.detail().contains("fallo")));
        assertTrue(response.items().stream().anyMatch(item -> "ACTION".equals(item.kind())
                && "Persisted detail".equals(item.detail())));
        assertTrue(response.items().stream().anyMatch(item -> "OPERATION".equals(item.kind())
                && "Operación".equals(item.title())
                && "UNKNOWN".equals(item.status())));
        assertTrue(response.items().stream().anyMatch(item -> "OPERATION".equals(item.kind())
                && item.detail().contains("2500")));
        assertTrue(response.items().stream().anyMatch(item -> "SUMMARY".equals(item.kind())
                && "Resumen de llamada".equals(item.title())
                && "RECORDED".equals(item.status())));
        assertTrue(response.items().stream().anyMatch(item -> "CALL_FINISHED".equals(item.kind())
                && item.detail().contains("proveedor informó")));
    }

    @Test
    void timelineRejectsMissingInputsAndMasksBlankPhones() {
        DemoRuntimeProperties noRuntime = new DemoRuntimeProperties();
        PlatformDemoTimelineService noRuntimeService = new PlatformDemoTimelineService(
                noRuntime,
                mock(DemoSessionRepository.class),
                mock(CallSessionRepository.class),
                mock(CallTranscriptRepository.class),
                mock(CallActionRepository.class),
                mock(BusinessOperationRepository.class),
                mock(CallSummaryRepository.class));

        assertThrows(IllegalArgumentException.class, () -> noRuntimeService.timeline(null));
        assertThrows(IllegalStateException.class, () -> noRuntimeService.timeline(UUID.randomUUID()));
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
