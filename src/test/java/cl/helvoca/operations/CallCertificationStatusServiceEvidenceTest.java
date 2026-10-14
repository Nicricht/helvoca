package cl.helvoca.operations;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallSummary;
import cl.helvoca.call.CallSummaryRepository;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallCertificationStatusServiceEvidenceTest {
    private static final String SID = "CA" + "a".repeat(32);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID callId = UUID.randomUUID();
    private final TenantProvider tenants = mock(TenantProvider.class);
    private final CallSessionRepository calls = mock(CallSessionRepository.class);
    private final CallActionRepository actions = mock(CallActionRepository.class);
    private final CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
    private final CallSummaryRepository summaries = mock(CallSummaryRepository.class);
    private final CallCertificationStatusService service = new CallCertificationStatusService(
            calls, actions, transcripts, summaries, tenants);
    private CallSession call;

    @BeforeEach
    void setup() {
        when(tenants.requireBusinessId()).thenReturn(tenantId);
        call = new CallSession();
        ReflectionTestUtils.setField(call, "id", callId);
        call.setBusinessId(tenantId);
        call.setCertification(true);
        call.setStatus(CallStatus.COMPLETED);
        call.setStartedAt(Instant.now().minusSeconds(60));
        call.setStreamStartedAt(Instant.now().minusSeconds(55));
        call.setStreamEndedAt(Instant.now().minusSeconds(5));
        call.setAiSetupCompletedAt(Instant.now().minusSeconds(52));
        call.setTelephonyProvider("twilio");
        call.setAiProvider("gemini");
        call.setProviderCallId(SID);
        call.setPhoneNumberId(UUID.randomUUID());
        when(calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantId))
                .thenReturn(Optional.of(call));
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(mock(CallTranscript.class)));
        when(summaries.findByCallId(callId)).thenReturn(Optional.of(mock(CallSummary.class)));
        List<CallAction> records = new ArrayList<>();
        UUID bookingId = UUID.randomUUID();
        for (String type : List.of("SERVICES_LISTED", "AVAILABILITY_CHECKED",
                "BOOKING_CREATED", "BOOKING_CANCELLED")) {
            CallAction action = mock(CallAction.class);
            when(action.getActionType()).thenReturn(type);
            when(action.isSuccess()).thenReturn(true);
            if (type.startsWith("BOOKING_")) {
                when(action.getEntityType()).thenReturn("BOOKING");
                when(action.getEntityId()).thenReturn(bookingId);
            }
            records.add(action);
        }
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(records);
    }

    @Test
    void completedTechnicalEvidenceIsNotCommercialAuthorization() {
        var status = service.current();
        assertTrue(status.available());
        assertEquals("PASSED", status.state());
        assertEquals(12, status.passedChecks());
        assertEquals(12, status.totalChecks());
        assertFalse(status.commercialCertified());
        assertTrue(status.checks().stream().allMatch(CallCertificationStatusService.Check::passed));
        verify(tenants).requireBusinessId();
        verifyNoMoreInteractions(tenants);
    }

    @Test
    void noCertificationRecordIsNotRunWithNoSideEffects() {
        when(calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantId))
                .thenReturn(Optional.empty());
        var status = service.current();
        assertFalse(status.available());
        assertEquals("NOT_RUN", status.state());
        assertEquals(12, status.totalChecks());
        assertFalse(status.commercialCertified());
        verifyNoInteractions(actions, transcripts, summaries);
    }

    @Test
    void crossTenantRepositoryLeakFailsClosedBeforeActionsAreRead() {
        call.setBusinessId(UUID.randomUUID());
        var status = service.current();
        assertFalse(status.available());
        assertEquals("NOT_RUN", status.state());
        verifyNoInteractions(actions, transcripts, summaries);
    }

    @Test
    void activeOrFailedCarrierNeverCountsAsCompletedTechnicalCertification() {
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStreamEndedAt(null);
        assertEquals("IN_PROGRESS", service.current().state());
        assertFalse(check("MEDIA_STOPPED").passed());
        assertFalse(check("CALL_COMPLETED").passed());

        call.setStreamEndedAt(Instant.now());
        assertEquals("FAILED", service.current().state());
        call.setStatus(CallStatus.FAILED);
        assertEquals("FAILED", service.current().state());
        assertFalse(check("CALL_COMPLETED").passed());
        call.setStatus(CallStatus.COMPLETED);
        assertEquals("PASSED", service.current().state());
    }

    @Test
    void simulatedMissingOrTamperedCarrierBindingNeverPasses() {
        call.setTelephonyProvider("simulator");
        assertFalse(check("CARRIER_BOUND").passed());
        call.setTelephonyProvider("twilio");
        call.setProviderCallId(null);
        assertFalse(check("CARRIER_BOUND").passed());
        call.setProviderCallId("CA-not-a-valid-call-sid");
        assertFalse(check("CARRIER_BOUND").passed());
        call.setProviderCallId(SID);
        call.setPhoneNumberId(null);
        assertFalse(check("CARRIER_BOUND").passed());
        call.setPhoneNumberId(UUID.randomUUID());
        assertTrue(check("CARRIER_BOUND").passed());
    }

    @Test
    void missingAiMetadataDoesNotClaimEndToEndCertification() {
        call.setAiProvider(null);
        assertFalse(check("AI_PROVIDER").passed());
        assertEquals("FAILED", service.current().state());
        call.setAiProvider(" ");
        assertFalse(check("AI_PROVIDER").passed());
        call.setAiProvider("gemini");
        assertTrue(check("AI_PROVIDER").passed());
        assertFalse(service.current().commercialCertified());
    }

    @Test
    void missingActionsTranscriptAndSummaryAreExplicitlyFailed() {
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of());
        when(transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId)).thenReturn(List.of());
        when(summaries.findByCallId(callId)).thenReturn(Optional.empty());
        var status = service.current();
        assertEquals("FAILED", status.state());
        assertFalse(status.commercialCertified());
        assertFalse(status.checks().stream().filter(x -> x.code().equals("TRANSCRIPT")
                || x.code().equals("SERVICES") || x.code().equals("AVAILABILITY")
                || x.code().equals("BOOKING_CREATED") || x.code().equals("BOOKING_CANCELLED")
                || x.code().equals("SUMMARY")).anyMatch(CallCertificationStatusService.Check::passed));
    }

    @Test
    void alternativeAvailabilityEvidenceIsAcceptedButNotAutomaticallyCommercial() {
        var list = actions.findAllByCallIdOrderByCreatedAtAsc(callId);
        CallAction replacement = mock(CallAction.class);
        when(replacement.isSuccess()).thenReturn(true);
        when(replacement.getActionType()).thenReturn("AVAILABILITY_LISTED");
        var copy = new ArrayList<>(list);
        copy.set(1, replacement);
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(copy);
        assertTrue(check("AVAILABILITY").passed());
        assertFalse(service.current().commercialCertified());
    }

    @Test
    void completedCarrierAndUnrelatedBookingCancellationAreNotEnough() {
        var records = new ArrayList<>(actions.findAllByCallIdOrderByCreatedAtAsc(callId));
        CallAction foreignCancellation = mock(CallAction.class);
        when(foreignCancellation.isSuccess()).thenReturn(true);
        when(foreignCancellation.getActionType()).thenReturn("BOOKING_CANCELLED");
        when(foreignCancellation.getEntityType()).thenReturn("BOOKING");
        when(foreignCancellation.getEntityId()).thenReturn(UUID.randomUUID());
        records.set(3, foreignCancellation);
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(records);

        var status = service.current();
        assertEquals("FAILED", status.state());
        assertTrue(status.checks().stream().filter(c -> c.code().equals("BOOKING_CREATED"))
                .findFirst().orElseThrow().passed());
        assertFalse(status.checks().stream().filter(c -> c.code().equals("BOOKING_CANCELLED"))
                .findFirst().orElseThrow().passed());
        assertFalse(status.commercialCertified());
    }

    @Test
    void bookingWithoutPersistedEntityDoesNotCountAsCreatedOrCancelled() {
        CallAction missing = mock(CallAction.class);
        when(missing.isSuccess()).thenReturn(true);
        when(missing.getActionType()).thenReturn("BOOKING_CREATED");
        when(missing.getEntityType()).thenReturn("BOOKING");
        when(missing.getEntityId()).thenReturn(null);
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(missing));

        assertFalse(check("BOOKING_CREATED").passed());
        assertFalse(check("BOOKING_CANCELLED").passed());
        assertEquals("FAILED", service.current().state());
    }

    private CallCertificationStatusService.Check check(String code) {
        return service.current().checks().stream().filter(x -> code.equals(x.code())).findFirst().orElseThrow();
    }
}
