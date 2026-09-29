package cl.helvoca.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BusinessAppearanceRequest(
        @NotBlank
        @Pattern(
                regexp = "^(cyan|blue|emerald|violet|amber)$",
                message = "theme must be one of cyan, blue, emerald, violet, amber")
        String theme
) {}
