package cl.helvoca.platform;

import java.time.Instant;
import java.util.UUID;

public record PlatformBusinessProvisioningResponse(
        UUID businessId,
        String businessName,
        String timezone,
        String language,
        String adminName,
        String adminEmail,
        UUID invitationId,
        String invitationStatus,
        Instant invitationExpiresAt,
        String invitePath,
        String onboardingPath
) {}
