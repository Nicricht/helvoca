package cl.helvoca.ai.realtime;

import java.util.UUID;

public record RealtimeCallContext(
        UUID callId,
        UUID businessId,
        UUID customerId,
        String callerNumber,
        String destinationNumber,
        String streamSid) {
}
