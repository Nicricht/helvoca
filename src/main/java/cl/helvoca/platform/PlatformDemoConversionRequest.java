package cl.helvoca.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformDemoConversionRequest(
        @NotBlank @Size(max = 150) String adminName,
        @NotBlank @Email @Size(max = 180) String adminEmail
) {}
