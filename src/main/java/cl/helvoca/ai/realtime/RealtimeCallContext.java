package cl.helvoca.ai.realtime;

import java.util.UUID;

public record RealtimeCallContext(
        UUID callId,
        UUID businessId,
        UUID customerId,
        String callerNumber,
        String destinationNumber,
        String streamSid,
        String voiceOverride) {

    public RealtimeCallContext(UUID callId,
                               UUID businessId,
                               UUID customerId,
                               String callerNumber,
                               String destinationNumber,
                               String streamSid) {
        this(callId, businessId, customerId, callerNumber, destinationNumber, streamSid, null);
    }

    public boolean voiceBakeOff() {
        return voiceOverride != null && !voiceOverride.isBlank();
    }

    public RealtimeCallContext withVoiceOverride(String value) {
        return new RealtimeCallContext(
                callId, businessId, customerId, callerNumber, destinationNumber, streamSid, value);
    }
}
