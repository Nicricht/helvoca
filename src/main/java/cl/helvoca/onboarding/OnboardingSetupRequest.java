package cl.helvoca.onboarding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record OnboardingSetupRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 60) String timezone,
        @NotBlank @Size(max = 10) String language,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "humanTransferPhone must use E.164 format")
        String humanTransferPhone,
        @NotNull List<@Valid ServiceInput> services,
        @NotNull List<@Valid HourInput> hours,
        List<@Valid KnowledgeInput> knowledge
) {
    public record ServiceInput(
            UUID id,
            @NotBlank @Size(max = 150) String name,
            String description,
            @NotNull @Positive Integer durationMinutes,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal price
    ) {}

    public record HourInput(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime
    ) {}

    public record KnowledgeInput(
            UUID id,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String category,
            @NotBlank String content
    ) {}
}
