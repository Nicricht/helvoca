package cl.helvoca.call;

import java.time.Instant;
import java.util.UUID;

public record CallActionResponse(
        UUID id,
        String actionType,
        boolean success,
        String entityType,
        UUID entityId,
        String detail,
        String errorCode,
        Instant createdAt
) {
    static CallActionResponse from(CallAction action) {
        return new CallActionResponse(
                action.getId(), action.getActionType(), action.isSuccess(), action.getEntityType(),
                action.getEntityId(), action.getDetail(), action.getErrorCode(), action.getCreatedAt());
    }
}
