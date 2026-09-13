package cl.helvoca.call;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CallResponse(
        UUID id,
        UUID customerId,
        UUID phoneNumberId,
        String telephonyProvider,
        String aiProvider,
        String providerCallId,
        String callerNumber,
        String destinationNumber,
        CallDirection direction,
        CallStatus status,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSeconds,
        BigDecimal estimatedTelephonyCostUsd,
        BigDecimal estimatedAiCostUsd,
        BigDecimal estimatedTotalCostUsd,
        String resolution,
        String streamSid,
        Instant streamStartedAt,
        Instant streamEndedAt
) {
    static CallResponse from(CallSession call) {
        return new CallResponse(call.getId(), call.getCustomerId(), call.getPhoneNumberId(),
                call.getTelephonyProvider(), call.getAiProvider(), call.getProviderCallId(),
                call.getCallerNumber(), call.getDestinationNumber(), call.getDirection(), call.getStatus(),
                call.getStartedAt(), call.getAnsweredAt(), call.getEndedAt(), call.getDurationSeconds(),
                call.getEstimatedTelephonyCostUsd(), call.getEstimatedAiCostUsd(), call.getEstimatedTotalCostUsd(),
                call.getResolution(), call.getStreamSid(), call.getStreamStartedAt(), call.getStreamEndedAt());
    }
}
