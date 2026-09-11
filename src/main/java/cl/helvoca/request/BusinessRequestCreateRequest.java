package cl.helvoca.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessRequestCreateRequest(
        @Size(max = 100) String category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String details,
        BusinessRequestPriority priority
) {}
