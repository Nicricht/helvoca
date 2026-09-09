package cl.helvoca.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 100) String category,
        @NotBlank String content,
        Boolean active
) {}
