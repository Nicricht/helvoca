package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessOperationEventService {
    private final BusinessOperationEventRepository events;
    private final TenantProvider tenantProvider;

    public BusinessOperationEventService(BusinessOperationEventRepository events,
                                         TenantProvider tenantProvider) {
        this.events = events;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<EventView> recent(UUID operationId) {
        UUID businessId = tenantProvider.requireBusinessId();
        List<BusinessOperationEvent> rows = operationId == null
                ? events.findTop100ByBusinessIdOrderBySequenceNoDesc(businessId)
                : events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId);
        return rows.stream().map(BusinessOperationEventService::view).toList();
    }

    private static EventView view(BusinessOperationEvent event) {
        return new EventView(
                event.getId(),
                event.getSequenceNo(),
                event.getOperationId(),
                event.getOperationType(),
                event.getEventType(),
                event.getChannel(),
                event.getSourceReferenceId(),
                event.getRevision(),
                event.getStatus(),
                event.getPreviousStatus(),
                event.getActorType(),
                event.getPayload(),
                event.getCreatedAt());
    }

    public record EventView(
            UUID id,
            Long sequenceNo,
            UUID operationId,
            BusinessOperation.Type operationType,
            String eventType,
            BusinessOrder.Source channel,
            UUID sourceReferenceId,
            Integer revision,
            BusinessOperation.Status status,
            BusinessOperation.Status previousStatus,
            BusinessOperationEvent.ActorType actorType,
            Map<String, Object> payload,
            Instant createdAt) { }
}
