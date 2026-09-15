package cl.helvoca.request;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.UniversalOperationWorkflowService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessRequestService {
    private final BusinessRequestRepository repository;
    private final TenantProvider tenantProvider;
    private final UniversalOperationWorkflowService universalOperations;
    private final BusinessOperationRepository operations;

    public BusinessRequestService(BusinessRequestRepository repository,
                                  TenantProvider tenantProvider,
                                  UniversalOperationWorkflowService universalOperations,
                                  BusinessOperationRepository operations) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
        this.universalOperations = universalOperations;
        this.operations = operations;
    }

    @Transactional(readOnly = true)
    public List<BusinessRequestDtos.Response> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(BusinessRequestDtos.Response::from).toList();
    }

    @Transactional
    public BusinessRequestDtos.Response create(BusinessRequestDtos.Create input) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest request = universalOperations.createRequest(
                businessId,
                null,
                null,
                clean(input.requestType(), 80),
                clean(input.title(), 200),
                input.description(),
                input.contactName(),
                input.contactPhone(),
                input.priority() == null ? RequestPriority.NORMAL : input.priority(),
                input.detailsJson(),
                RequestSource.MANUAL);
        return BusinessRequestDtos.Response.from(request);
    }

    @Transactional
    public BusinessRequestDtos.Response setStatus(UUID id, RequestStatus status) {
        if (status == null) throw new IllegalArgumentException("status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest request = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        request.setStatus(status);
        request = repository.save(request);

        BusinessRequest saved = request;
        operations.findByIdAndBusinessId(saved.getOperationId(), businessId).ifPresent(operation -> {
            operation.setStatus(status == RequestStatus.CANCELLED
                    ? BusinessOperation.Status.CANCELLED
                    : BusinessOperation.Status.CONFIRMED);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
            metadata.put("projectionStatus", status.name());
            operation.setMetadata(metadata);
            operations.save(operation);
        });
        return BusinessRequestDtos.Response.from(request);
    }

    @Transactional
    public BusinessRequest createFromAi(UUID businessId, UUID customerId, UUID callId,
                                        String requestType, String title, String description,
                                        String contactName, String contactPhone,
                                        RequestPriority priority, String detailsJson) {
        return createFromAi(businessId, customerId, callId, requestType, title, description,
                contactName, contactPhone, priority, detailsJson, RequestSource.AI_CALL);
    }

    @Transactional
    public BusinessRequest createFromAi(UUID businessId, UUID customerId, UUID callId,
                                        String requestType, String title, String description,
                                        String contactName, String contactPhone,
                                        RequestPriority priority, String detailsJson,
                                        RequestSource source) {
        return universalOperations.createRequest(
                businessId,
                customerId,
                callId,
                clean(requestType, 80),
                clean(title, 200),
                description,
                contactName,
                contactPhone,
                priority == null ? RequestPriority.NORMAL : priority,
                detailsJson,
                source == null ? RequestSource.AI_CALL : source);
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is missing");
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
