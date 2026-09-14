package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessOperationCapabilityService {
    private final BusinessOperationCapabilityRepository repository;
    private final TenantProvider tenantProvider;

    public BusinessOperationCapabilityService(BusinessOperationCapabilityRepository repository,
                                              TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Set<BusinessOperationCapability> current() {
        return enabled(tenantProvider.requireBusinessId());
    }

    @Transactional(readOnly = true)
    public Set<BusinessOperationCapability> enabled(UUID businessId) {
        EnumSet<BusinessOperationCapability> out = EnumSet.noneOf(BusinessOperationCapability.class);
        repository.findAllByBusinessIdOrderByCapabilityAsc(businessId)
                .forEach(grant -> out.add(grant.getCapability()));
        return Set.copyOf(out);
    }

    @Transactional(readOnly = true)
    public Set<String> allowedToolNames(UUID businessId) {
        return BusinessOperationCapability.toolNamesFor(enabled(businessId));
    }

    @Transactional(readOnly = true)
    public boolean isToolAllowed(UUID businessId, String toolName) {
        return BusinessOperationCapability.fromToolName(toolName)
                .map(capability -> repository.existsByBusinessIdAndCapability(businessId, capability))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, BusinessOperationCapability capability) {
        return repository.existsByBusinessIdAndCapability(businessId, capability);
    }

    @Transactional
    public Set<BusinessOperationCapability> replaceCurrent(Set<BusinessOperationCapability> capabilities) {
        UUID businessId = tenantProvider.requireBusinessId();
        EnumSet<BusinessOperationCapability> desired = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(BusinessOperationCapability.class)
                : EnumSet.copyOf(capabilities);

        // ORDER and QUOTE consume the universal catalog. DELIVERY also requires
        // ORDER because delivery is a fulfillment mode of an order.
        if (desired.contains(BusinessOperationCapability.ORDER)
                || desired.contains(BusinessOperationCapability.QUOTE)) {
            desired.add(BusinessOperationCapability.CATALOG);
        }
        if (desired.contains(BusinessOperationCapability.DELIVERY)) {
            desired.add(BusinessOperationCapability.ORDER);
            desired.add(BusinessOperationCapability.CATALOG);
        }

        repository.deleteAllByBusinessId(businessId);
        repository.flush();
        List<BusinessOperationCapabilityGrant> grants = desired.stream().map(capability -> {
            BusinessOperationCapabilityGrant grant = new BusinessOperationCapabilityGrant();
            grant.setBusinessId(businessId);
            grant.setCapability(capability);
            return grant;
        }).toList();
        repository.saveAll(grants);
        repository.flush();
        return Set.copyOf(new LinkedHashSet<>(desired));
    }
}
