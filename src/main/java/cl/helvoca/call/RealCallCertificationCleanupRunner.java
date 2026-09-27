package cl.helvoca.call;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Disabled-by-default, idempotent cleanup for a booking created by an explicitly
 * authorized real outbound certification call.
 *
 * The runner refuses to touch a booking unless the exact completed Twilio
 * OUTBOUND call has a successful BOOKING_CREATED action pointing at the exact
 * booking id, the booking belongs to the same tenant and it was created by the
 * AI call channel.
 */
@Component
public class RealCallCertificationCleanupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealCallCertificationCleanupRunner.class);

    private final boolean enabled;
    private final String callIdValue;
    private final String bookingIdValue;
    private final String allowedPhone;
    private final CallSessionRepository calls;
    private final CallActionRepository actions;
    private final BookingRepository bookings;
    private final TenantDatabaseContext databaseContext;

    public RealCallCertificationCleanupRunner(
            @Value("${RECEPVOZ_CERTIFICATION_CLEANUP_ENABLED:false}") boolean enabled,
            @Value("${RECEPVOZ_CERTIFICATION_CLEANUP_CALL_ID:}") String callIdValue,
            @Value("${RECEPVOZ_CERTIFICATION_CLEANUP_BOOKING_ID:}") String bookingIdValue,
            @Value("${TWILIO_CERTIFICATION_ALLOWED_TO:}") String allowedPhone,
            CallSessionRepository calls,
            CallActionRepository actions,
            BookingRepository bookings,
            TenantDatabaseContext databaseContext) {
        this.enabled = enabled;
        this.callIdValue = callIdValue;
        this.bookingIdValue = bookingIdValue;
        this.allowedPhone = allowedPhone;
        this.calls = calls;
        this.actions = actions;
        this.bookings = bookings;
        this.databaseContext = databaseContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID callId = parse(callIdValue);
        UUID bookingId = parse(bookingIdValue);
        if (callId == null || bookingId == null || blank(allowedPhone)) {
            log.error("RECEPVOZ_REAL_CERT_CLEANUP blocked: missing_or_invalid_guard");
            return;
        }

        CallSession call = databaseContext.callAsSystem(() -> calls.findById(callId).orElse(null));
        if (!safeCall(call)) {
            log.error("RECEPVOZ_REAL_CERT_CLEANUP blocked: call_guard_failed call={}", callId);
            return;
        }

        CleanupResult result = databaseContext.callAsTenant(
                call.getBusinessId(),
                () -> cleanup(call, bookingId));

        if (result.cancelled()) {
            log.warn("RECEPVOZ_REAL_CERT_CLEANUP SUCCESS call={} booking={} action=cancel_booking",
                    callId, bookingId);
        } else if (result.alreadyCancelled()) {
            log.info("RECEPVOZ_REAL_CERT_CLEANUP IDEMPOTENT call={} booking={} status=already_cancelled",
                    callId, bookingId);
        } else {
            log.error("RECEPVOZ_REAL_CERT_CLEANUP blocked: {} call={} booking={}",
                    result.reason(), callId, bookingId);
        }
    }

    private boolean safeCall(CallSession call) {
        return call != null
                && call.getBusinessId() != null
                && call.getStatus() != null
                && call.getStatus().terminal()
                && call.getDirection() == CallDirection.OUTBOUND
                && "twilio".equalsIgnoreCase(call.getTelephonyProvider())
                && allowedPhone.trim().equals(normalize(call.getCallerNumber()));
    }

    CleanupResult cleanup(CallSession call, UUID bookingId) {
        if (call == null || bookingId == null) return CleanupResult.blocked("invalid_context");

        List<CallAction> persistedActions = actions.findAllByCallIdOrderByCreatedAtAsc(call.getId());
        boolean linked = persistedActions.stream().anyMatch(action ->
                action.isSuccess()
                        && "BOOKING_CREATED".equals(action.getActionType())
                        && bookingId.equals(action.getEntityId()));
        if (!linked) return CleanupResult.blocked("booking_not_created_by_call");

        Booking booking = bookings.findByIdAndBusinessId(bookingId, call.getBusinessId()).orElse(null);
        if (booking == null) return CleanupResult.blocked("booking_not_found");
        if (booking.getSource() != BookingSource.AI_CALL) {
            return CleanupResult.blocked("booking_source_not_ai_call");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return CleanupResult.alreadyCancelledResult();
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return CleanupResult.blocked("booking_not_confirmed");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookings.saveAndFlush(booking);
        return CleanupResult.cancelledResult();
    }

    private static UUID parse(String value) {
        try {
            return blank(value) ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record CleanupResult(boolean cancelled, boolean alreadyCancelled, String reason) {
        static CleanupResult cancelledResult() {
            return new CleanupResult(true, false, null);
        }

        static CleanupResult alreadyCancelledResult() {
            return new CleanupResult(false, true, null);
        }

        static CleanupResult blocked(String reason) {
            return new CleanupResult(false, false, reason);
        }
    }
}
