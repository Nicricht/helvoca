package cl.helvoca.operations;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Read-only repository. Mutations are intentionally unavailable at the Java layer. */
public interface BusinessOperationEventRepository extends Repository<BusinessOperationEvent, UUID> {
    List<BusinessOperationEvent> findTop100ByBusinessIdOrderBySequenceNoDesc(UUID businessId);

    List<BusinessOperationEvent> findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(
            UUID businessId, UUID operationId);
}
