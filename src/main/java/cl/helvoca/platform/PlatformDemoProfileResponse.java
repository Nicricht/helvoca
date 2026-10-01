package cl.helvoca.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlatformDemoProfileResponse(
        UUID id,
        String displayName,
        String businessName,
        String timezone,
        String language,
        Map<String, Object> catalog,
        Map<String, Object> hours,
        Map<String, Object> knowledge,
        String greeting,
        String instructions,
        List<String> capabilities,
        String presenterNotes,
        Map<String, Object> sourceMetadata,
        Instant createdAt,
        Instant updatedAt
) {}
