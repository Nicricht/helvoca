package cl.helvoca.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeItemResponse(
        UUID id,
        String title,
        String category,
        String content,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static KnowledgeItemResponse from(KnowledgeItem item) {
        return new KnowledgeItemResponse(
                item.getId(), item.getTitle(), item.getCategory(), item.getContent(),
                item.isActive(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }
}
