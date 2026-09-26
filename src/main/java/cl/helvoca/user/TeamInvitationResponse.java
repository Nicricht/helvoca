package cl.helvoca.user;

import java.time.Instant;
import java.util.UUID;

public record TeamInvitationResponse(
        UUID id,
        UUID businessId,
        String businessName,
        String name,
        String email,
        String role,
        Instant expiresAt,
        String status,
        String invitePath
) {}
