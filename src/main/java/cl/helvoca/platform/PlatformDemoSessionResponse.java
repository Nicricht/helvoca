package cl.helvoca.platform;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformDemoSessionResponse(
        UUID id,
        UUID correlationId,
        UUID demoProfileId,
        UUID runtimeBusinessId,
        DemoSessionState state,
        String configurationRevision,
        Instant stagedAt,
        Instant startedAt,
        Instant finishedAt,
        String failureReason,
        PlatformDemoReadinessResponse readiness,
        Instant createdAt,
        Instant updatedAt,
        String operator,
        Map<String, Object> configurationSnapshot,
        Map<String, Object> readinessSnapshot,
        String externalEffectsState,
        String paymentState,
        UUID convertedPilotBusinessId
) {}
