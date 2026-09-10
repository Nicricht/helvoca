package cl.helvoca.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutoOnboardingRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 2048) String sourceUrl
) {}
