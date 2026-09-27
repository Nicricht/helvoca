package cl.helvoca.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformCertificationRunRequest(
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")
        String runId
) {}
