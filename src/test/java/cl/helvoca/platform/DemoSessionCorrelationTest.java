package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.messaging.MessagingConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoSessionCorrelationTest {

    @Test
    void onlyActiveSessionOnConfiguredRuntimeCorrelates() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoSession active = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(active, "id", sessionId);
        active.markReady();
        active.markActive();
        when(sessions.findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
                runtimeId, List.of(DemoSessionState.ACTIVE))).thenReturn(Optional.of(active));

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));

        assertEquals(Optional.of(sessionId), service.activeSessionIdForBusiness(runtimeId));
        assertTrue(service.activeSessionIdForBusiness(UUID.randomUUID()).isEmpty());
    }


    @Test
    void inboundVoiceAutomaticallyActivatesReadySessionWhenReadinessIsStillSafe() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        DemoInboundSafetyReadiness readiness = mock(DemoInboundSafetyReadiness.class);
        AuditService audit = mock(AuditService.class);

        DemoSession ready = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(ready, "id", sessionId);
        ready.markStaged();
        ready.markReady();

        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(ready));
        when(sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(ready));
        when(sessions.saveAndFlush(ready)).thenReturn(ready);
        when(readiness.safeSnapshot(runtimeId)).thenReturn(Optional.of(safeSnapshot(runtimeId)));

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, calls, mock(MessagingConversationRepository.class));
        service.setInboundSafety(readiness);
        service.setAudit(audit);

        assertEquals(Optional.of(sessionId), service.activeOrActivateForInboundVoice(runtimeId));
        assertEquals(DemoSessionState.ACTIVE, ready.getState());
        assertNotNull(ready.getStartedAt());
        verify(sessions).saveAndFlush(ready);
        verify(audit).success(runtimeId, "DEMO_SESSION_AUTO_ACTIVE", "DEMO_SESSION", sessionId);
    }

    @Test
    void inboundVoiceDoesNotActivateReadySessionWhenSafetyReadinessHasDegraded() {
        UUID runtimeId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoInboundSafetyReadiness readiness = mock(DemoInboundSafetyReadiness.class);
        AuditService audit = mock(AuditService.class);

        DemoSession ready = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(ready, "id", UUID.randomUUID());
        ready.markReady();
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(ready));

        when(readiness.safeSnapshot(runtimeId)).thenReturn(Optional.empty());

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));
        service.setInboundSafety(readiness);
        service.setAudit(audit);

        assertTrue(service.activeOrActivateForInboundVoice(runtimeId).isEmpty());
        assertEquals(DemoSessionState.READY, ready.getState());
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }


    @Test
    void inboundVoiceRechecksSafetyAfterLockAndFailsClosedIfReadinessDegrades() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoInboundSafetyReadiness readiness = mock(DemoInboundSafetyReadiness.class);
        AuditService audit = mock(AuditService.class);

        DemoSession ready = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(ready, "id", sessionId);
        ready.markStaged();
        ready.markReady();

        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(ready));
        when(sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(ready));
        when(readiness.safeSnapshot(runtimeId))
                .thenReturn(Optional.of(safeSnapshot(runtimeId)), Optional.empty());

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));
        service.setInboundSafety(readiness);
        service.setAudit(audit);

        assertTrue(service.activeOrActivateForInboundVoice(runtimeId).isEmpty());
        assertEquals(DemoSessionState.READY, ready.getState());
        verify(readiness, times(2)).safeSnapshot(runtimeId);
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void terminalCorrelatedCallFinishesSessionOnlyAfterLastLiveCallEnds() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AuditService audit = mock(AuditService.class);

        DemoSession active = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(active, "id", sessionId);
        active.markReady();
        active.markActive();

        CallSession terminal = new CallSession();
        terminal.setBusinessId(runtimeId);
        terminal.setDemoSessionId(sessionId);
        terminal.setStatus(CallStatus.COMPLETED);

        when(sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(active));
        when(calls.countByBusinessIdAndDemoSessionIdAndStatusIn(eq(runtimeId), eq(sessionId), anyCollection()))
                .thenReturn(1L, 0L);
        when(sessions.saveAndFlush(active)).thenReturn(active);

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, calls, mock(MessagingConversationRepository.class));
        service.setAudit(audit);

        service.finishIfTerminal(terminal);
        assertEquals(DemoSessionState.ACTIVE, active.getState());

        service.finishIfTerminal(terminal);
        assertEquals(DemoSessionState.FINISHED, active.getState());
        assertNotNull(active.getFinishedAt());
        verify(audit).success(runtimeId, "DEMO_SESSION_AUTO_FINISHED", "DEMO_SESSION", sessionId);
    }


    @Test
    void sourceCorrelationUsesPersistedCallAndRequiresActiveSession() {
        UUID runtimeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);

        DemoSession active = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(active, "id", sessionId);
        active.markReady();
        active.markActive();
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)).thenReturn(Optional.of(active));

        CallSession call = new CallSession();
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        when(calls.findByIdAndBusinessId(callId, runtimeId)).thenReturn(Optional.of(call));

        DemoSessionCorrelationService service =
                new DemoSessionCorrelationService(properties, sessions, calls, conversations);

        assertEquals(Optional.of(sessionId), service.resolveForSource(runtimeId, callId));
        verifyNoInteractions(conversations);

        active.markFinished();
        assertTrue(service.resolveForSource(runtimeId, callId).isEmpty());
    }
    private static java.util.Map<String, Object> safeSnapshot(UUID runtimeId) {
        java.util.LinkedHashMap<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("runtimeBusinessId", runtimeId);
        snapshot.put("runtime", "READY");
        snapshot.put("voiceNumber", "READY");
        snapshot.put("voiceAi", "READY");
        snapshot.put("businessData", "READY");
        snapshot.put("operations", "READY");
        snapshot.put("payment", "SANDBOX_ONLY");
        snapshot.put("externalEffects", "DISARMED");
        return snapshot;
    }
}
