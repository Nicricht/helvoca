package cl.helvoca.quality;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationReplayCaptureServiceTest {

    @Test
    void capturesAnonymizedReplayAndPreservesQualityFindingSignature() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);

        CallSession call = mock(CallSession.class);
        when(call.getCustomerId()).thenReturn(customerId);
        when(call.getCallerNumber()).thenReturn("+56912345678");
        when(call.getDestinationNumber()).thenReturn("+56220000000");
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));

        Customer customer = mock(Customer.class);
        when(customer.getName()).thenReturn("Nicolás Vega");
        when(customer.getPhone()).thenReturn("+56912345678");
        when(customer.getEmail()).thenReturn("nico@example.com");
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));

        CallTranscript t1 = transcript("USER", "Soy Nicolás Vega, mi número es +56912345678.");
        CallTranscript t2 = transcript("ASSISTANT", "¿A nombre de quién sería la reserva?");
        CallTranscript t3 = transcript("USER", "Nicolás Vega.");
        CallTranscript t4 = transcript("ASSISTANT", "¿A nombre de quién sería la reserva?");
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(t1, t2, t3, t4));

        CallAction a1 = action("BOOKING_CREATED", bookingId, "nico@example.com");
        when(actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(businessId, callId))
                .thenReturn(List.of(a1));

        ConversationReplayCaptureService service = new ConversationReplayCaptureService(
                calls, transcripts, actions, customers, tenant);

        ConversationReplayFixture fixture = service.capture(callId);

        assertEquals(1, fixture.schemaVersion());
        assertEquals(16, fixture.sourceFingerprint().length());
        assertFalse(fixture.expected().passed());
        assertTrue(fixture.expected().findingCodes().contains("REPEATED_QUESTION"));

        String allText = fixture.turns().toString() + fixture.actions().toString();
        assertFalse(allText.contains("Nicolás Vega"));
        assertFalse(allText.contains("+56912345678"));
        assertFalse(allText.contains("nico@example.com"));
        assertFalse(allText.contains(bookingId.toString()));
        assertTrue(allText.contains("[CUSTOMER_NAME]"));
        assertEquals("ENTITY_1", fixture.actions().get(0).entityRef());
    }

    @Test
    void refusesCrossTenantReplayBeforeReadingConversationContent() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.empty());

        ConversationReplayCaptureService service = new ConversationReplayCaptureService(
                calls, transcripts, actions, customers, tenant);

        assertThrows(NotFoundException.class, () -> service.capture(callId));
        verifyNoInteractions(transcripts, actions, customers);
    }

    private static CallTranscript transcript(String speaker, String content) {
        CallTranscript value = mock(CallTranscript.class);
        when(value.getSpeaker()).thenReturn(speaker);
        when(value.getContent()).thenReturn(content);
        return value;
    }

    private static CallAction action(String type, UUID entityId, String detail) {
        CallAction value = mock(CallAction.class);
        when(value.getActionType()).thenReturn(type);
        when(value.isSuccess()).thenReturn(true);
        when(value.getEntityType()).thenReturn("BOOKING");
        when(value.getEntityId()).thenReturn(entityId);
        when(value.getDetail()).thenReturn(detail);
        return value;
    }
}
