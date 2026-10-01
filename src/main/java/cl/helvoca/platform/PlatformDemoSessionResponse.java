package cl.helvoca.platform;

import java.time.Instant;
import java.util.UUID;

public record PlatformDemoSessionResponse(
        UUID id,
        UUID profileId,
        UUID runtimeBusinessId,
        DemoSessionState state,
        String expectedParticipantPhone,
        String configurationRevision,
        String failureReason,
        Instant createdAt,
        Instant preparedAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static PlatformDemoSessionResponse from(DemoSession value) {
        return new PlatformDemoSessionResponse(
                value.getId(),
                value.getProfileId(),
                value.getRuntimeBusinessId(),
                value.getState(),
                value.getExpectedParticipantPhone(),
                value.getConfigurationRevision(),
                value.getFailureReason(),
                value.getCreatedAt(),
                value.getPreparedAt(),
                value.getStartedAt(),
                value.getFinishedAt());
    }
}
