package cl.helvoca.request;

import java.time.Instant;
import java.util.UUID;

public record BusinessRequestResponse(
        UUID id,
        UUID customerId,
        UUID callId,
        String category,
        String subject,
        String details,
        BusinessRequestPriority priority,
        BusinessRequestStatus status,
        BusinessRequestSource source,
        Instant createdAt,
        Instant updatedAt
) {
    public static BusinessRequestResponse from(BusinessRequest request) {
        return new BusinessRequestResponse(
                request.getId(), request.getCustomerId(), request.getCallId(), request.getCategory(),
                request.getSubject(), request.getDetails(), request.getPriority(), request.getStatus(),
                request.getSource(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
