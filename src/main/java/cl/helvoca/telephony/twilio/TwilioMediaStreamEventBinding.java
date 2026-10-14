package cl.helvoca.telephony.twilio;

import org.json.JSONObject;

/**
 * Carrier StreamSid continuity, independent of already-signed call admission.
 * VERIFIED_EXISTING stays ineligible for call routing until an authorized release.
 */
final class TwilioMediaStreamEventBinding {
    private TwilioMediaStreamEventBinding() { }

    static boolean consistentStart(JSONObject event, JSONObject start) {
        String outer = event.optString("streamSid", null);
        String inner = start.optString("streamSid", null);
        return outer == null || outer.isBlank()
                || inner == null || inner.isBlank()
                || outer.equals(inner);
    }

    static boolean matchesStream(String admittedSid, JSONObject event) {
        return admittedSid != null && !admittedSid.isBlank()
                && admittedSid.equals(event.optString("streamSid", null));
    }
}
