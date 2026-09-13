package cl.helvoca.call;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CallCertificationService {
    private static final Logger log = LoggerFactory.getLogger(CallCertificationService.class);
    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_MILLIS = 250L;

    private final CallSessionRepository calls;
    private final CallActionRepository actions;
    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;
    private final BookingRepository bookings;

    public CallCertificationService(CallSessionRepository calls,
                                    CallActionRepository actions,
                                    CallTranscriptRepository transcripts,
                                    CallSummaryRepository summaries,
                                    BookingRepository bookings) {
        this.calls = calls;
        this.actions = actions;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.bookings = bookings;
    }

    @Async
    public void verifyAfterCall(UUID callId) {
        if (callId == null) return;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            CallSession call = calls.findById(callId).orElse(null);
            if (call == null || !call.isCertification()) return;
            if (call.getStreamEndedAt() != null && summaries.findByCallId(callId).isPresent()) break;
            if (!sleep()) return;
        }
        verifyAndCleanup(callId);
    }

    @Transactional
    public CertificationResult verifyAndCleanup(UUID callId) {
        CallSession call = calls.findById(callId).orElse(null);
        if (call == null) return CertificationResult.failed("call_not_found", 0);
        if (!call.isCertification()) return CertificationResult.failed("not_a_certification_call", 0);

        List<CallAction> persistedActions = actions.findAllByCallIdOrderByCreatedAtAsc(callId);
        List<String> reasons = new ArrayList<>();

        if (call.getAiSetupCompletedAt() == null) reasons.add("gemini_setup_incomplete");
        if (call.getStreamStartedAt() == null) reasons.add("media_stream_not_started");
        if (call.getStreamEndedAt() == null) reasons.add("media_stream_not_stopped");
        if (transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId).isEmpty()) reasons.add("transcript_missing");
        if (persistedActions.isEmpty()) reasons.add("call_action_missing");
        if (summaries.findByCallId(callId).isEmpty()) reasons.add("summary_missing");

        requireSuccessful(persistedActions, "SERVICES_LISTED", reasons);
        if (!hasSuccessful(persistedActions, "AVAILABILITY_CHECKED")
                && !hasSuccessful(persistedActions, "AVAILABILITY_LISTED")) {
            reasons.add("availability_tool_not_successful");
        }
        requireSuccessful(persistedActions, "BOOKING_CREATED", reasons);
        requireSuccessful(persistedActions, "BOOKING_CANCELLED", reasons);

        int cleanupCount = cleanupCertificationBookings(call, persistedActions, reasons);
        CertificationResult result = reasons.isEmpty()
                ? CertificationResult.success(cleanupCount)
                : CertificationResult.failed(String.join(",", reasons), cleanupCount);

        if (result.success()) {
            log.info("RECEPVOZ_CALL_CERTIFICATION SUCCESS call={} actions={} cleanup_cancelled={}",
                    callId, persistedActions.size(), cleanupCount);
        } else {
            log.error("RECEPVOZ_CALL_CERTIFICATION FAILED call={} reason={} actions={} cleanup_cancelled={}",
                    callId, result.reason(), persistedActions.size(), cleanupCount);
        }
        return result;
    }

    private int cleanupCertificationBookings(CallSession call,
                                             List<CallAction> persistedActions,
                                             List<String> reasons) {
        int cleanupCount = 0;
        for (CallAction action : persistedActions) {
            if (!action.isSuccess() || !"BOOKING_CREATED".equals(action.getActionType())) continue;
            UUID bookingId = action.getEntityId();
            if (bookingId == null) {
                reasons.add("created_booking_entity_missing");
                continue;
            }
            Booking booking = bookings.findByIdAndBusinessId(bookingId, call.getBusinessId()).orElse(null);
            if (booking == null) {
                reasons.add("created_booking_not_found");
                continue;
            }
            if (booking.getStatus() != BookingStatus.CANCELLED) {
                reasons.add("created_booking_not_cancelled");
                booking.setStatus(BookingStatus.CANCELLED);
                bookings.saveAndFlush(booking);
                cleanupCount++;
                log.warn("RECEPVOZ_CALL_CERTIFICATION CLEANUP call={} entity_id={} action=cancel_booking",
                        call.getId(), bookingId);
            }
        }
        return cleanupCount;
    }

    private static void requireSuccessful(List<CallAction> actions,
                                          String actionType,
                                          List<String> reasons) {
        if (!hasSuccessful(actions, actionType)) {
            reasons.add(actionType.toLowerCase() + "_not_successful");
        }
    }

    private static boolean hasSuccessful(List<CallAction> actions, String actionType) {
        return actions.stream().anyMatch(action -> action.isSuccess() && actionType.equals(action.getActionType()));
    }

    private static boolean sleep() {
        try {
            Thread.sleep(RETRY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record CertificationResult(boolean success, String reason, int cleanupCancelled) {
        static CertificationResult success(int cleanupCancelled) {
            return new CertificationResult(true, null, cleanupCancelled);
        }

        static CertificationResult failed(String reason, int cleanupCancelled) {
            return new CertificationResult(false, reason, cleanupCancelled);
        }
    }
}
