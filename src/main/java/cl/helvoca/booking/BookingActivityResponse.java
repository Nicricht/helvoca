package cl.helvoca.booking;

import cl.helvoca.audit.AuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BookingActivityResponse(
        UUID id,
        String action,
        String actorType,
        UUID actorUserId,
        String actorName,
        String actorRole,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Instant createdAt) {

    static BookingActivityResponse from(AuditLog log) {
        return new BookingActivityResponse(
                log.getId(),
                log.getAction(),
                log.getActorType(),
                log.getActorUserId(),
                log.getActorName(),
                log.getActorRole(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getCreatedAt());
    }
}
