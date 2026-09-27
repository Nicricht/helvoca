package cl.helvoca.platform;

import java.time.Instant;

public record PlatformCertificationRunResponse(
        String runId,
        String status,
        String requestedBy,
        Instant requestedAt,
        Instant claimedAt,
        Instant expiresAt,
        String providerCallSid,
        Instant completedAt,
        String failureReason
) {}
