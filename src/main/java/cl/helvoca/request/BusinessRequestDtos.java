package cl.helvoca.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class BusinessRequestDtos {
    private BusinessRequestDtos() {}

    public record Create(
            @NotBlank String requestType,
            @NotBlank String title,
            String description,
            String contactName,
            String contactPhone,
            RequestPriority priority,
            String detailsJson
    ) {}

    public record UpdateStatus(RequestStatus status) {}

    public record Response(
            UUID id,
            UUID customerId,
            UUID callId,
            String requestType,
            String title,
            String description,
            String contactName,
            String contactPhone,
            RequestPriority priority,
            RequestStatus status,
            RequestSource source,
            String detailsJson,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(BusinessRequest r) {
            return new Response(r.getId(), r.getCustomerId(), r.getCallId(), r.getRequestType(), r.getTitle(),
                    r.getDescription(), r.getContactName(), r.getContactPhone(), r.getPriority(), r.getStatus(),
                    r.getSource(), r.getDetailsJson(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
