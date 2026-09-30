package cl.helvoca.operations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BusinessOrderLineRepository extends JpaRepository<BusinessOrderLine, UUID> {
    List<BusinessOrderLine> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);
    List<BusinessOrderLine> findAllByOrderIdInOrderByCreatedAtAsc(Collection<UUID> orderIds);
}
