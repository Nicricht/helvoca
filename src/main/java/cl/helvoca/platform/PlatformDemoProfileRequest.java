package cl.helvoca.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record PlatformDemoProfileRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 60) String timezone,
        @NotBlank @Size(max = 10) String language,
        @NotNull Map<String, Object> catalog,
        @NotNull Map<String, Object> hours,
        @NotNull Map<String, Object> knowledge,
        @NotBlank @Size(max = 4000) String greeting,
        @Size(max = 12000) String instructions,
        @NotNull List<@Size(max = 40) String> capabilities,
        @Size(max = 4000) String presenterNotes,
        @NotNull Map<String, Object> sourceMetadata
) {}
