package cl.helvoca.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String resourceType,
        UUID resourceId,
        String result,
        String actorType,
        UUID actorUserId,
        String actorName,
        String actorEmail,
        String actorRole,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Instant createdAt
) {
    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getResult(),
                log.getActorType(),
                log.getActorUserId(),
                log.getActorName(),
                log.getActorEmail(),
                log.getActorRole(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getCreatedAt());
    }
}
