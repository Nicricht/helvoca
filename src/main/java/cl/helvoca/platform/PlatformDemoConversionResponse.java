package cl.helvoca.platform;

import java.time.Instant;
import java.util.UUID;

public record PlatformDemoConversionResponse(
        UUID sessionId,
        UUID pilotBusinessId,
        String pilotBusinessName,
        String mode,
        UUID invitationId,
        String invitationStatus,
        Instant invitationExpiresAt,
        String invitePath,
        String onboardingPath,
        boolean idempotentReplay
) {}
