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
    public List<BusinessRequestDtos.Response> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(BusinessRequestDtos.Response::from).toList();
    }

    @Transactional
    public BusinessRequestDtos.Response create(BusinessRequestDtos.Create input) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest request = new BusinessRequest();
        request.setBusinessId(businessId);
        request.setRequestType(clean(input.requestType(), 80));
        request.setTitle(clean(input.title(), 200));
        request.setDescription(blankToNull(input.description()));
        request.setContactName(blankToNull(input.contactName()));
        request.setContactPhone(blankToNull(input.contactPhone()));
        request.setPriority(input.priority() == null ? RequestPriority.NORMAL : input.priority());
        request.setStatus(RequestStatus.OPEN);
        request.setSource(RequestSource.MANUAL);
        request.setDetailsJson(blankToNull(input.detailsJson()));
        return BusinessRequestDtos.Response.from(repository.saveAndFlush(request));
    }

    @Transactional
    public BusinessRequestDtos.Response setStatus(UUID id, RequestStatus status) {
        if (status == null) throw new IllegalArgumentException("status is required");
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessRequest request = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        request.setStatus(status);
        return BusinessRequestDtos.Response.from(repository.save(request));
    }

    @Transactional
    public BusinessRequest createFromAi(UUID businessId, UUID customerId, UUID callId,
                                        String requestType, String title, String description,
                                        String contactName, String contactPhone,
                                        RequestPriority priority, String detailsJson) {
        BusinessRequest request = new BusinessRequest();
        request.setBusinessId(businessId);
        request.setCustomerId(customerId);
        request.setCallId(callId);
        request.setRequestType(clean(requestType, 80));
        request.setTitle(clean(title, 200));
        request.setDescription(blankToNull(description));
        request.setContactName(blankToNull(contactName));
        request.setContactPhone(blankToNull(contactPhone));
        request.setPriority(priority == null ? RequestPriority.NORMAL : priority);
        request.setStatus(RequestStatus.OPEN);
        request.setSource(RequestSource.AI_CALL);
        request.setDetailsJson(blankToNull(detailsJson));
        return repository.saveAndFlush(request);
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is missing");
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
