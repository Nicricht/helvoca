package cl.helvoca.servicecatalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceItemResponse(
        UUID id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceItemResponse from(ServiceItem item) {
        return new ServiceItemResponse(
                item.getId(), item.getName(), item.getDescription(), item.getDurationMinutes(),
                item.getPrice(), item.isActive(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }
}
