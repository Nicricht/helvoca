package cl.helvoca.platform;

import cl.helvoca.call.CallSession;
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
}
