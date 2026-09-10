package cl.helvoca.telephony.twilio.trial;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialVoiceImmediateMutationAckTest {

    @Test
    void successfulBookingMutationsUseImmediateAcknowledgement() {
        String success = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject().put("bookingId", "booking-1"))
                .put("error", JSONObject.NULL)
                .toString();

        assertTrue(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("create_booking", success));
        assertTrue(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("reschedule_booking", success));
        assertTrue(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("cancel_booking", success));
    }

    @Test
    void readsAndFailedMutationsStillUseNormalToolResponseFlow() {
        String success = new JSONObject().put("success", true).toString();
        String failure = new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", "BOOKING_NOT_FOUND"))
                .toString();

        assertFalse(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("list_customer_bookings", success));
        assertFalse(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("cancel_booking", failure));
        assertFalse(TrialVoiceConversationService.shouldAcknowledgeMutationImmediately("cancel_booking", "not-json"));
    }
}
