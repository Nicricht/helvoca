package cl.helvoca.call;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CallCertificationBookingEvidencePhase8Test {
    private static CallAction action(String type, boolean success, String entityType, UUID id) {
        CallAction a = new CallAction();
        a.setActionType(type);
        a.setSuccess(success);
        a.setEntityType(entityType);
        a.setEntityId(id);
        return a;
    }

    @Test
    void requiresARealCreatedBookingWithBusinessEntityIdentity() {
        UUID id = UUID.randomUUID();
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(List.of()));
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(
                java.util.Arrays.asList((CallAction) null)));
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(
                List.of(action("BOOKING_CREATED", false, "BOOKING", id))));
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(
                List.of(action("BOOKING_CANCELLED", true, "BOOKING", id))));
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(
                List.of(action("BOOKING_CREATED", true, "CUSTOMER", id))));
        assertFalse(CallCertificationBookingEvidence.hasCreatedBooking(
                List.of(action("BOOKING_CREATED", true, "BOOKING", null))));
        assertTrue(CallCertificationBookingEvidence.hasCreatedBooking(
                List.of(action("BOOKING_CREATED", true, "BOOKING", id))));
    }

    @Test
    void cannotPassWithNoBookingOrUnrelatedCancellationOrFailedAction() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CallAction created = action("BOOKING_CREATED", true, "BOOKING", a);
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(List.of()));
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(created)));
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(created, action("BOOKING_CANCELLED", true, "BOOKING", b))));
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(created, action("BOOKING_CANCELLED", false, "BOOKING", a))));
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(created, action("BOOKING_CANCELLED", true, "CUSTOMER", a))));
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(created, action("BOOKING_CANCELLED", true, "BOOKING", null))));
    }

    @Test
    void everyCreatedBookingNeedsItsOwnSuccessfulCancellation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CallAction createA = action("BOOKING_CREATED", true, "BOOKING", a);
        CallAction createB = action("BOOKING_CREATED", true, "BOOKING", b);
        CallAction cancelA = action("BOOKING_CANCELLED", true, "BOOKING", a);
        CallAction cancelB = action("BOOKING_CANCELLED", true, "BOOKING", b);

        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(createA, createB, cancelA)));
        assertTrue(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(createA, createB, cancelB, cancelA)));
        assertTrue(CallCertificationBookingEvidence.hasMatchingCancellation(
                List.of(createA, createA, cancelA)));
    }

    @Test
    void syntheticSuccessfulBookingWithNoEntityCannotCreateFalsePositive() {
        UUID id = UUID.randomUUID();
        CallAction unbound = action("BOOKING_CREATED", true, "BOOKING", null);
        assertFalse(CallCertificationBookingEvidence.hasMatchingCancellation(List.of(
                unbound, action("BOOKING_CANCELLED", true, "BOOKING", id))));
        assertTrue(CallCertificationBookingEvidence.hasMatchingCancellation(List.of(
                unbound,
                action("BOOKING_CREATED", true, "BOOKING", id),
                action("BOOKING_CANCELLED", true, "BOOKING", id))));
    }
}
