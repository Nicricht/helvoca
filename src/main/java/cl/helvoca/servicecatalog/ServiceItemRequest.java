package cl.helvoca.servicecatalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServiceItemRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull @Positive Integer durationMinutes,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        Boolean active
) {}
