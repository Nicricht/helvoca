package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DemoVoiceCallLifecycleObserverTest {

    @Test
    void readyDemoSessionIsDurablyCorrelatedAndActivatedByInboundRuntimeCall() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(runtimeId);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AuditService audit = mock(AuditService.class);

        DemoSession session = readySession(sessionId, runtimeId);
        CallSession call = inboundCall(UUID.randomUUID(), runtimeId, CallStatus.RINGING);

        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(calls.saveAndFlush(call)).thenReturn(call);
        when(sessions.saveAndFlush(session)).thenReturn(session);

        new DemoVoiceCallLifecycleObserver(properties, sessions, calls, audit)
                .onInboundCallStarted(call);

        assertEquals(sessionId, call.getDemoSessionId());
        assertEquals(DemoSessionState.ACTIVE, session.getState());
        assertNotNull(session.getStartedAt());
        verify(calls).saveAndFlush(call);
        verify(sessions).saveAndFlush(session);
        verify(audit).success(runtimeId, "DEMO_SESSION_ACTIVE", "DEMO_SESSION", sessionId);
    }

    @Test
    void duplicateInboundObservationIsIdempotent() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(runtimeId);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AuditService audit = mock(AuditService.class);

        DemoSession session = readySession(sessionId, runtimeId);
        session.markActive();
        CallSession call = inboundCall(UUID.randomUUID(), runtimeId, CallStatus.IN_PROGRESS);
        call.setDemoSessionId(sessionId);

        when(sessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        DemoVoiceCallLifecycleObserver observer =
                new DemoVoiceCallLifecycleObserver(properties, sessions, calls, audit);
        observer.onInboundCallStarted(call);
        observer.onInboundCallStarted(call);

        verify(calls, never()).saveAndFlush(any());
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void anotherTenantAndPreparingSessionNeverAttach() {
        UUID runtimeId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(runtimeId);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AuditService audit = mock(AuditService.class);
        DemoVoiceCallLifecycleObserver observer =
                new DemoVoiceCallLifecycleObserver(properties, sessions, calls, audit);

        CallSession foreign = inboundCall(UUID.randomUUID(), UUID.randomUUID(), CallStatus.RINGING);
        observer.onInboundCallStarted(foreign);

        DemoSession preparing = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(preparing, "id", UUID.randomUUID());
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(preparing));
        CallSession runtimeCall = inboundCall(UUID.randomUUID(), runtimeId, CallStatus.RINGING);
        observer.onInboundCallStarted(runtimeCall);

        assertNull(foreign.getDemoSessionId());
        assertNull(runtimeCall.getDemoSessionId());
        verify(calls, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void terminalCallFinishesSessionOnlyAfterLastCorrelatedLiveCallEnds() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(runtimeId);
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AuditService audit = mock(AuditService.class);

        DemoSession session = readySession(sessionId, runtimeId);
        session.markActive();
        CallSession completed = inboundCall(UUID.randomUUID(), runtimeId, CallStatus.COMPLETED);
        completed.setDemoSessionId(sessionId);

        when(sessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(calls.countByDemoSessionIdAndStatusIn(eq(sessionId), anyCollection()))
                .thenReturn(1L, 0L);
        when(sessions.saveAndFlush(session)).thenReturn(session);

        DemoVoiceCallLifecycleObserver observer =
                new DemoVoiceCallLifecycleObserver(properties, sessions, calls, audit);

        observer.onCallUpdated(completed);
        assertEquals(DemoSessionState.ACTIVE, session.getState());

        observer.onCallUpdated(completed);
        assertEquals(DemoSessionState.FINISHED, session.getState());
        assertNotNull(session.getFinishedAt());
        verify(audit).success(runtimeId, "DEMO_SESSION_FINISHED", "DEMO_SESSION", sessionId);
    }

    private static DemoRuntimeProperties properties(UUID runtimeId) {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());
        return properties;
    }

    private static DemoSession readySession(UUID id, UUID runtimeId) {
        DemoSession session = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(session, "id", id);
        ReflectionTestUtils.setField(session, "createdAt", Instant.now());
        ReflectionTestUtils.setField(session, "updatedAt", Instant.now());
        session.markStaged();
        session.markReady();
        return session;
    }

    private static CallSession inboundCall(UUID id, UUID businessId, CallStatus status) {
        CallSession call = new CallSession();
        ReflectionTestUtils.setField(call, "id", id);
        call.setBusinessId(businessId);
        call.setDirection(CallDirection.INBOUND);
        call.setTelephonyProvider("twilio");
        call.setProviderCallId("CA-" + id);
        call.setDestinationNumber("+14355550000");
        call.setCallerNumber("+56911111111");
        call.setStartedAt(Instant.now());
        call.setStatus(status);
        return call;
    }
}
