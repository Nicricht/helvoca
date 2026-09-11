package cl.helvoca.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveUnansweredQuestionRequest(
        @NotBlank @Size(max = 4000) String answer
) {}
