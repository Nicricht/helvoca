package cl.helvoca.platform;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoSessionCorrelationBranchCoverageTest {

    @Test
    void activeLookupRejectsMissingNullAndForeignRuntimeInputs() {
        DemoRuntimeProperties missing = new DemoRuntimeProperties();
        DemoSessionCorrelationService missingService = new DemoSessionCorrelationService(
                missing, mock(DemoSessionRepository.class), mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));
        assertTrue(missingService.activeSessionIdForBusiness(UUID.randomUUID()).isEmpty());

        UUID runtimeId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());
        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, sessions, mock(CallSessionRepository.class),
                mock(MessagingConversationRepository.class));

        assertTrue(service.activeSessionIdForBusiness(null).isEmpty());
        assertTrue(service.activeSessionIdForBusiness(UUID.randomUUID()).isEmpty());
        when(sessions.findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
                runtimeId, List.of(DemoSessionState.ACTIVE))).thenReturn(Optional.empty());
        assertTrue(service.activeSessionIdForBusiness(runtimeId).isEmpty());
    }

    @Test
    void resolveRejectsInvalidInputsAndFallsBackFromCallToConversation() {
        UUID runtimeId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        DemoSessionCorrelationService service =
                new DemoSessionCorrelationService(properties, sessions, calls, conversations);

        assertTrue(service.resolveForSource(null, sourceId).isEmpty());
        assertTrue(service.resolveForSource(runtimeId, null).isEmpty());
        assertTrue(service.resolveForSource(UUID.randomUUID(), sourceId).isEmpty());

        when(calls.findByIdAndBusinessId(sourceId, runtimeId)).thenReturn(Optional.empty());
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(runtimeId);
        conversation.setDemoSessionId(sessionId);
        when(conversations.findByIdAndBusinessId(sourceId, runtimeId))
                .thenReturn(Optional.of(conversation));

        DemoSession active = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(active, "id", sessionId);
        active.markReady();
        active.markActive();
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(active));

        assertEquals(Optional.of(sessionId), service.resolveForSource(runtimeId, sourceId));
    }

    @Test
    void resolveReturnsEmptyWhenNeitherSourceNorActiveSessionSupportsCandidate() {
        UUID runtimeId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(runtimeId.toString());

        DemoSessionRepository sessions = mock(DemoSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        DemoSessionCorrelationService service =
                new DemoSessionCorrelationService(properties, sessions, calls, conversations);

        when(calls.findByIdAndBusinessId(sourceId, runtimeId)).thenReturn(Optional.empty());
        when(conversations.findByIdAndBusinessId(sourceId, runtimeId)).thenReturn(Optional.empty());
        assertTrue(service.resolveForSource(runtimeId, sourceId).isEmpty());

        UUID sessionId = UUID.randomUUID();
        CallSession call = new CallSession();
        call.setBusinessId(runtimeId);
        call.setDemoSessionId(sessionId);
        when(calls.findByIdAndBusinessId(sourceId, runtimeId)).thenReturn(Optional.of(call));

        DemoSession finished = DemoSession.preparing(UUID.randomUUID(), runtimeId, "rev");
        ReflectionTestUtils.setField(finished, "id", sessionId);
        finished.markReady();
        finished.markActive();
        finished.markFinished();
        when(sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId))
                .thenReturn(Optional.of(finished));

        assertTrue(service.resolveForSource(runtimeId, sourceId).isEmpty());
    }

    @Test
    void resolveWithMissingConfiguredRuntimeFailsClosedBeforeRepositories() {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        DemoSessionCorrelationService service = new DemoSessionCorrelationService(
                properties, mock(DemoSessionRepository.class), calls, conversations);

        assertTrue(service.resolveForSource(UUID.randomUUID(), UUID.randomUUID()).isEmpty());
        verifyNoInteractions(calls, conversations);
    }
}
