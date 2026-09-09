package cl.helvoca.servicecatalog;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ServiceCatalogService {
    private final ServiceItemRepository services;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public ServiceCatalogService(ServiceItemRepository services, TenantProvider tenantProvider, AuditService auditService) {
        this.services = services;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ServiceItemResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return services.findAllByBusinessIdOrderByNameAsc(businessId)
                .stream().map(ServiceItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceItemResponse get(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        return ServiceItemResponse.from(requireItem(id, businessId));
    }

    @Transactional
    public ServiceItemResponse create(ServiceItemRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        validateUniqueName(businessId, request.name());
        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        apply(item, request);
        ServiceItem saved = services.save(item);
        auditService.success(businessId, "SERVICE_CREATE", "SERVICE", saved.getId());
        return ServiceItemResponse.from(saved);
    }

    @Transactional
    public ServiceItemResponse update(UUID id, ServiceItemRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        ServiceItem item = requireItem(id, businessId);
        if (!item.getName().equalsIgnoreCase(request.name())) {
            validateUniqueName(businessId, request.name());
        }
        apply(item, request);
        auditService.success(businessId, "SERVICE_UPDATE", "SERVICE", id);
        return ServiceItemResponse.from(item);
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        ServiceItem item = requireItem(id, businessId);
        item.setActive(false);
        auditService.success(businessId, "SERVICE_DEACTIVATE", "SERVICE", id);
    }

    public ServiceItem requireActiveEntity(UUID id, UUID businessId) {
        ServiceItem item = requireItem(id, businessId);
        if (!item.isActive()) throw new ConflictException("Service is inactive");
        return item;
    }

    private ServiceItem requireItem(UUID id, UUID businessId) {
        return services.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Service not found"));
    }

    private void validateUniqueName(UUID businessId, String name) {
        if (services.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ConflictException("A service with that name already exists");
        }
    }

    private static void apply(ServiceItem item, ServiceItemRequest request) {
        item.setName(request.name());
        item.setDescription(request.description());
        item.setDurationMinutes(request.durationMinutes());
        item.setPrice(request.price());
        if (request.active() != null) item.setActive(request.active());
    }
}
