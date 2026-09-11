package cl.helvoca.capability;

import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessCapabilityService {
    private final BusinessCapabilityRepository repository;
    private final TenantProvider tenantProvider;

    public BusinessCapabilityService(BusinessCapabilityRepository repository, TenantProvider tenantProvider) {
        this.repository = repository;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public BusinessCapabilityResponse current() {
        return new BusinessCapabilityResponse(resolve(tenantProvider.requireBusinessId()));
    }

    @Transactional(readOnly = true)
    public Map<BusinessCapabilityCode, Boolean> resolve(UUID businessId) {
        List<BusinessCapability> rows = repository.findAllByBusinessIdOrderByCodeAsc(businessId);
        Map<BusinessCapabilityCode, Boolean> result = new EnumMap<>(BusinessCapabilityCode.class);
        if (rows.isEmpty()) {
            for (BusinessCapabilityCode code : BusinessCapabilityCode.values()) result.put(code, true);
            return result;
        }
        for (BusinessCapabilityCode code : BusinessCapabilityCode.values()) result.put(code, false);
        rows.forEach(row -> result.put(row.getCode(), row.isEnabled()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, BusinessCapabilityCode code) {
        List<BusinessCapability> rows = repository.findAllByBusinessIdOrderByCodeAsc(businessId);
        if (rows.isEmpty()) return true;
        return rows.stream().anyMatch(row -> row.getCode() == code && row.isEnabled());
    }

    @Transactional
    public BusinessCapabilityResponse update(BusinessCapabilityUpdateRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        Set<BusinessCapabilityCode> enabled = request.enabled();
        for (BusinessCapabilityCode code : BusinessCapabilityCode.values()) {
            BusinessCapability row = repository.findByBusinessIdAndCode(businessId, code)
                    .orElseGet(BusinessCapability::new);
            if (row.getId() == null) {
                row.setBusinessId(businessId);
                row.setCode(code);
            }
            row.setEnabled(enabled.contains(code));
            repository.save(row);
        }
        return new BusinessCapabilityResponse(resolve(businessId));
    }
}
