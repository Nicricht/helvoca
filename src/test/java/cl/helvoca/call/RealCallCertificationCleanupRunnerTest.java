package cl.helvoca.call;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RealCallCertificationCleanupRunnerTest {
    private static final String PROVIDER_CALL_ID = "CA11111111111111111111111111111111";

    @Test
    void cancelsOnlyBookingPersistedByExactCompletedAuthorizedCall() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);

        CallAction created = new CallAction();
        created.setActionType("BOOKING_CREATED");
        created.setSuccess(true);
        created.setEntityId(bookingId);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setSource(BookingSource.AI_CALL);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(calls.findById(callId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(created));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true,
                callId.toString(),
                bookingId.toString(),
                PROVIDER_CALL_ID,
                allowed,
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookings).saveAndFlush(booking);
    }

    @Test
    void refusesCleanupWhenCallerIsNotExplicitlyAllowlisted() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, "+56900000000");

        when(calls.findById(callId)).thenReturn(Optional.of(call));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true,
                callId.toString(),
                bookingId.toString(),
                PROVIDER_CALL_ID,
                "+56966939611",
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(actions);
        verifyNoInteractions(bookings);
    }

    @Test
    void refusesCleanupWhenTwilioProviderCallIdDoesNotMatch() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);
        when(call.getProviderCallId()).thenReturn("CA22222222222222222222222222222222");
        when(calls.findById(callId)).thenReturn(Optional.of(call));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true,
                callId.toString(),
                bookingId.toString(),
                PROVIDER_CALL_ID,
                allowed,
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(actions);
        verifyNoInteractions(bookings);
    }

    @Test
    void refusesBookingThatWasNotCreatedByTheExactCall() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);

        CallAction other = new CallAction();
        other.setActionType("BOOKING_CREATED");
        other.setSuccess(true);
        other.setEntityId(UUID.randomUUID());

        when(calls.findById(callId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(other));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true,
                callId.toString(),
                bookingId.toString(),
                PROVIDER_CALL_ID,
                allowed,
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void disabledCleanupDoesNotReadProductionData() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                false,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                PROVIDER_CALL_ID,
                "+56966939611",
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(calls, actions, bookings);
    }

    @Test
    void invalidCleanupIdentifiersFailClosedBeforeRepositories() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true,
                "not-a-uuid",
                "also-not-a-uuid",
                PROVIDER_CALL_ID,
                "+56966939611",
                calls,
                actions,
                bookings,
                new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(calls, actions, bookings);
    }

    @Test
    void alreadyCancelledBookingIsIdempotent() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);

        CallAction created = new CallAction();
        created.setActionType("BOOKING_CREATED");
        created.setSuccess(true);
        created.setEntityId(bookingId);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setSource(BookingSource.AI_CALL);
        booking.setStatus(BookingStatus.CANCELLED);

        when(calls.findById(callId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(created));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true, callId.toString(), bookingId.toString(), PROVIDER_CALL_ID, allowed,
                calls, actions, bookings, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void refusesCleanupWhenLinkedBookingNoLongerExists() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);

        CallAction created = new CallAction();
        created.setActionType("BOOKING_CREATED");
        created.setSuccess(true);
        created.setEntityId(bookingId);

        when(calls.findById(callId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(created));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.empty());

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true, callId.toString(), bookingId.toString(), PROVIDER_CALL_ID, allowed,
                calls, actions, bookings, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void refusesNonAiCallBookingEvenWhenActionIdMatches() {
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String allowed = "+56966939611";

        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallActionRepository actions = mock(CallActionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSession call = safeCall(callId, businessId, allowed);

        CallAction created = new CallAction();
        created.setActionType("BOOKING_CREATED");
        created.setSuccess(true);
        created.setEntityId(bookingId);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setSource(BookingSource.ADMIN);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(calls.findById(callId)).thenReturn(Optional.of(call));
        when(actions.findAllByCallIdOrderByCreatedAtAsc(callId)).thenReturn(List.of(created));
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        RealCallCertificationCleanupRunner runner = new RealCallCertificationCleanupRunner(
                true, callId.toString(), bookingId.toString(), PROVIDER_CALL_ID, allowed,
                calls, actions, bookings, new TenantDatabaseContext());

        runner.run(mock(ApplicationArguments.class));

        verify(bookings, never()).saveAndFlush(any());
    }

    private static CallSession safeCall(UUID callId, UUID businessId, String caller) {
        CallSession call = mock(CallSession.class);
        when(call.getId()).thenReturn(callId);
        when(call.getBusinessId()).thenReturn(businessId);
        when(call.getStatus()).thenReturn(CallStatus.COMPLETED);
        // outbound-test is currently materialized by the media stream path as
        // an INBOUND lifecycle row, so direction is intentionally not trusted
        // as certification evidence.
        when(call.getDirection()).thenReturn(CallDirection.INBOUND);
        when(call.getTelephonyProvider()).thenReturn("twilio");
        when(call.getProviderCallId()).thenReturn(PROVIDER_CALL_ID);
        when(call.getCallerNumber()).thenReturn(caller);
        return call;
    }
}
