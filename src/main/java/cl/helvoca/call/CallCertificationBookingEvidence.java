package cl.helvoca.call;

import java.util.List;
import java.util.UUID;

/**
 * Correlates booking creation/cancellation evidence from one persisted call.
 *
 * A successful unrelated cancellation must never certify a created booking.
 * Reads only already-persisted CallAction evidence; no provider or DB access.
 */
public final class CallCertificationBookingEvidence {
    private CallCertificationBookingEvidence() { }

    public static boolean hasCreatedBooking(List<CallAction> actions) {
        return actions.stream().anyMatch(action -> valid(action, "BOOKING_CREATED"));
    }

    public static boolean hasMatchingCancellation(List<CallAction> actions) {
        List<UUID> createdBookingIds = actions.stream()
                .filter(action -> valid(action, "BOOKING_CREATED"))
                .map(CallAction::getEntityId)
                .distinct()
                .toList();

        if (createdBookingIds.isEmpty()) return false;

        // Every booking actually created during certification must have
        // a successful cancellation of that same booking in this call.
        return createdBookingIds.stream().allMatch(bookingId -> actions.stream()
                .anyMatch(action -> valid(action, "BOOKING_CANCELLED")
                        && bookingId.equals(action.getEntityId())));
    }

    private static boolean valid(CallAction action, String expectedType) {
        return action != null
                && action.isSuccess()
                && expectedType.equals(action.getActionType())
                && "BOOKING".equals(action.getEntityType())
                && action.getEntityId() != null;
    }
}
