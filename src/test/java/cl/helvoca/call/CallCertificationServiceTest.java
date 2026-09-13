package cl.helvoca.call;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallCertificationServiceTest {

    @Test
    void succeedsOnlyWhenPersistedMilestonesAndExpectedToolsArePresent() {
        Fixture fixture = new Fixture();
        UUID callId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CallSession call = certifiedCall();
        Booking booking = booking(BookingStatus.CANCELLED);

        when(fixture.calls.findById(callId)).thenReturn(Optional.of(call));
        when(fixture.transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(mock(CallTranscript.class)));
        when(fixture.summaries.findByCallId(callId)).thenReturn(Optional.of(mock(CallSummary.class)));
        when(fixture.actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(
                action("SERVICES_LISTED", true, null),
                action("AVAILABILITY_CHECKED", true, null),
                action("BOOKING_CREATED", true, bookingId),
                action("BOOKING_CANCELLED", true, bookingId)));
        when(fixture.bookings.findByIdAndBusinessId(bookingId, call.getBusinessId()))
                .thenReturn(Optional.of(booking));

        var result = fixture.service.verifyAndCleanup(callId);

        assertTrue(result.success());
        assertNull(result.reason());
        assertEquals(0, result.cleanupCancelled());
        verify(fixture.bookings, never()).saveAndFlush(any());
    }

    @Test
    void uncancelledCertificationBookingIsSafelyCancelledAndCertificationFails() {
        Fixture fixture = new Fixture();
        UUID callId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CallSession call = certifiedCall();
        Booking booking = booking(BookingStatus.CONFIRMED);

        when(fixture.calls.findById(callId)).thenReturn(Optional.of(call));
        when(fixture.transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId))
                .thenReturn(List.of(mock(CallTranscript.class)));
        when(fixture.summaries.findByCallId(callId)).thenReturn(Optional.of(mock(CallSummary.class)));
        when(fixture.actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(
                action("SERVICES_LISTED", true, null),
                action("AVAILABILITY_LISTED", true, null),
                action("BOOKING_CREATED", true, bookingId)));
        when(fixture.bookings.findByIdAndBusinessId(bookingId, call.getBusinessId()))
                .thenReturn(Optional.of(booking));
        when(fixture.bookings.saveAndFlush(booking)).thenReturn(booking);

        var result = fixture.service.verifyAndCleanup(callId);

        assertFalse(result.success());
        assertTrue(result.reason().contains("booking_cancelled_not_successful"));
        assertTrue(result.reason().contains("created_booking_not_cancelled"));
        assertEquals(1, result.cleanupCancelled());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(fixture.bookings).saveAndFlush(booking);
    }

    @Test
    void neverTouchesBookingsForNonCertificationCalls() {
        Fixture fixture = new Fixture();
        UUID callId = UUID.randomUUID();
        CallSession call = certifiedCall();
        call.setCertification(false);
        when(fixture.calls.findById(callId)).thenReturn(Optional.of(call));

        var result = fixture.service.verifyAndCleanup(callId);

        assertFalse(result.success());
        assertEquals("not_a_certification_call", result.reason());
        verifyNoInteractions(fixture.bookings, fixture.actions, fixture.transcripts, fixture.summaries);
    }

    private static CallSession certifiedCall() {
        CallSession call = new CallSession();
        call.setBusinessId(UUID.randomUUID());
        call.setCertification(true);
        call.setStreamStartedAt(Instant.now().minusSeconds(10));
        call.setStreamEndedAt(Instant.now());
        call.setAiSetupCompletedAt(Instant.now().minusSeconds(9));
        return call;
    }

    private static CallAction action(String type, boolean success, UUID entityId) {
        CallAction action = new CallAction();
        action.setActionType(type);
        action.setSuccess(success);
        if (entityId != null) {
            action.setEntityType("BOOKING");
            action.setEntityId(entityId);
        }
        return action;
    }

    private static Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setStatus(status);
        return booking;
    }

    private static final class Fixture {
        private final CallSessionRepository calls = mock(CallSessionRepository.class);
        private final CallActionRepository actions = mock(CallActionRepository.class);
        private final CallTranscriptRepository transcripts = mock(CallTranscriptRepository.class);
        private final CallSummaryRepository summaries = mock(CallSummaryRepository.class);
        private final BookingRepository bookings = mock(BookingRepository.class);
        private final CallCertificationService service = new CallCertificationService(
                calls, actions, transcripts, summaries, bookings);
    }
}
