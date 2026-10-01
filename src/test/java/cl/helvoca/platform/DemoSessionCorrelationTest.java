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
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        DemoSession ready = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(ready, "id", sessionId);
        ready.markStaged();
        ready.markReady();

        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(ready));
        when(sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(ready));
        when(sessions.saveAndFlush(ready)).thenReturn(ready);
        when(readiness.readiness()).thenReturn(safeReadiness(runtimeId));

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, calls, mock(MessagingConversationRepository.class));
        service.setReadiness(readiness);
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
        PlatformDemoReadinessService readiness = mock(PlatformDemoReadinessService.class);
        AuditService audit = mock(AuditService.class);

        DemoSession ready = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(ready, "id", UUID.randomUUID());
        ready.markReady();
        when(sessions.findPreparedForRuntime(runtimeId)).thenReturn(Optional.of(ready));

        PlatformDemoReadinessResponse unsafe = new PlatformDemoReadinessResponse(
                true, runtimeId,
                item("READY"), item("READY"), item("UNAVAILABLE"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED"));
        when(readiness.readiness()).thenReturn(unsafe);

        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));
        service.setReadiness(readiness);
        service.setAudit(audit);

        assertTrue(service.activeOrActivateForInboundVoice(runtimeId).isEmpty());
        assertEquals(DemoSessionState.READY, ready.getState());
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
    private static PlatformDemoReadinessResponse safeReadiness(UUID runtimeId) {
        return new PlatformDemoReadinessResponse(
                true, runtimeId,
                item("READY"), item("READY"), item("READY"), item("READY"), item("READY"),
                item("NOT_CONFIGURED"), item("SANDBOX_ONLY"), item("DISARMED"));
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state) {
        return new PlatformDemoReadinessResponse.ReadinessItem(state, state);
    }
}
