package cl.helvoca.quality;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationQualityServiceTest {

    @Test
    void analyzesPersistedCallInsideAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);

        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStatus(CallStatus.COMPLETED);
        call.setResolution("BOOKING_CREATED");
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        CallTranscript user = mock(CallTranscript.class);
        when(user.getSpeaker()).thenReturn("USER");
        when(user.getContent()).thenReturn("Eso sería todo, chao.");
        CallTranscript assistant = mock(CallTranscript.class);
        when(assistant.getSpeaker()).thenReturn("ASSISTANT");
        when(assistant.getContent()).thenReturn("Chao, que estés muy bien.");
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(user, assistant));

        CallAction action = mock(CallAction.class);
        when(action.getActionType()).thenReturn("BOOKING_CREATED");
        when(action.isSuccess()).thenReturn(true);
        when(action.getEntityType()).thenReturn("BOOKING");
        when(action.getEntityId()).thenReturn(bookingId);
        when(action.getDetail()).thenReturn("created");
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(businessId, callId))
                .thenReturn(List.of(action));

        ConversationQualityService service =
                new ConversationQualityService(calls, transcripts, actions, tenant);

        var result = service.analyze(callId);

        assertEquals(callId, result.callId());
        assertEquals("COMPLETED", result.callStatus());
        assertEquals("BOOKING_CREATED", result.resolution());
        assertEquals(2, result.totalTurns());
        assertEquals(1, result.userTurns());
        assertEquals(1, result.assistantTurns());
        assertEquals(1, result.successfulActions());
        assertTrue(result.quality().passed());
    }

    @Test
    void rejectsCrossTenantCallBeforeReadingTranscriptOrActions() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.empty());

        ConversationQualityService service =
                new ConversationQualityService(calls, transcripts, actions, tenant);

        assertThrows(NotFoundException.class, () -> service.analyze(callId));
        verifyNoInteractions(transcripts, actions);
    }
}
