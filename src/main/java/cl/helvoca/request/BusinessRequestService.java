package cl.helvoca.request;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BusinessRequestService {
    private final BusinessRequestRepository repository;
    private final TenantProvider tenantProvider;

    public BusinessRequestService(BusinessRequestRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public List<BusinessRequestResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().map(BusinessRequestResponse::from).toList();
    }

    @Transactional
    public BusinessRequestResponse create(BusinessRequestCreateRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest item = build(businessId, null, null, request.category(), request.subject(), request.details(),
                request.priority(), BusinessRequestSource.ADMIN);
        return BusinessRequestResponse.from(repository.saveAndFlush(item));
    }

    @Transactional
    public BusinessRequest createFromCall(UUID businessId,
                                          UUID customerId,
                                          UUID callId,
                                          String category,
                                          String subject,
                                          String details,
                                          BusinessRequestPriority priority) {
        BusinessRequest item = build(businessId, customerId, callId, category, subject, details, priority,
                BusinessRequestSource.AI_CALL);
        return repository.saveAndFlush(item);
    }

    @Transactional
    public BusinessRequestResponse setStatus(UUID id, BusinessRequestStatusRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest item = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        item.setStatus(request.status());
        return BusinessRequestResponse.from(repository.save(item));
    }

    private static BusinessRequest build(UUID businessId,
                                         UUID customerId,
                                         UUID callId,
                                         String category,
                                         String subject,
                                         String details,
                                         BusinessRequestPriority priority,
                                         BusinessRequestSource source) {
        BusinessRequest item = new BusinessRequest();
        item.setBusinessId(businessId);
        item.setCustomerId(customerId);
        item.setCallId(callId);
        item.setCategory(trimToNull(category, 100));
        item.setSubject(required(subject, "El asunto es obligatorio", 200));
        item.setDetails(required(details, "El detalle es obligatorio", 4000));
        item.setPriority(priority == null ? BusinessRequestPriority.NORMAL : priority);
        item.setStatus(BusinessRequestStatus.OPEN);
        item.setSource(source);
        return item;
    }

    private static String required(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String trimToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
