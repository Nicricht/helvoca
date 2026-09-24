package cl.helvoca.jobs;

import java.time.Instant;
import java.util.UUID;

public record PersistentJob(
        UUID id,
        UUID businessId,
        UUID operationId,
        Type jobType,
        Status status,
        String idempotencyKey,
        String payloadJson,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseExpiresAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public enum Type {
        OUTBOUND_MESSAGE_DISPATCH,
        META_WHATSAPP_AI_REPLY,
        META_WHATSAPP_RECOVERY_REPLY,
        CALENDAR_EVENT_SYNC,
        WHATSAPP_INBOUND_TEXT_PROCESS,
        WHATSAPP_INBOUND_AUDIO_PROCESS
    }

    public enum Status {
        PENDING, RUNNING, SUCCEEDED, FAILED, DEAD_LETTER, CANCELLED
    }
}
