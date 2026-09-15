package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessOperationItemRepository extends JpaRepository<BusinessOperationItem, UUID> {
    List<BusinessOperationItem> findAllByOperationIdOrderByCreatedAtAsc(UUID operationId);
    long deleteAllByOperationId(UUID operationId);
}
