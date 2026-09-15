package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BusinessOperationCapabilityService {
    private final AiAgentService aiAgents;
    private final TenantProvider tenantProvider;

    public BusinessOperationCapabilityService(AiAgentService aiAgents,
                                              TenantProvider tenantProvider) {
        this.aiAgents = aiAgents;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Set<BusinessOperationCapability> current() {
        return enabled(tenantProvider.requireBusinessId());
    }

    @Transactional(readOnly = true)
    public Set<BusinessOperationCapability> enabled(UUID businessId) {
        AiAgent agent = aiAgents.runtime(businessId);
        if (!agent.isActive()) return Set.of();
        return BusinessOperationCapability.fromAiCapabilities(agent.getCapabilities());
    }

    @Transactional(readOnly = true)
    public Set<String> allowedToolNames(UUID businessId) {
        return aiAgents.allowedToolNames(businessId).stream()
                .filter(BusinessOperationCapability::isCommercialToolName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public boolean isToolAllowed(UUID businessId, String toolName) {
        return BusinessOperationCapability.isCommercialToolName(toolName)
                && aiAgents.toolAllowed(businessId, toolName);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, BusinessOperationCapability capability) {
        if (capability == null) return false;
        AiAgent agent = aiAgents.runtime(businessId);
        return agent.isActive() && agent.getCapabilities().containsAll(capability.aiCapabilities());
    }

    @Transactional
    public Set<BusinessOperationCapability> replaceCurrent(Set<BusinessOperationCapability> capabilities) {
        EnumSet<BusinessOperationCapability> desired = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(BusinessOperationCapability.class)
                : EnumSet.copyOf(capabilities);

        // High-level presets normalize only real functional dependencies. ORDER
        // and QUOTE depend on the catalog; DELIVERY is now an autonomous domain.
        if (desired.contains(BusinessOperationCapability.ORDER)
                || desired.contains(BusinessOperationCapability.QUOTE)) {
            desired.add(BusinessOperationCapability.CATALOG);
        }

        aiAgents.replaceCommercialCapabilities(BusinessOperationCapability.aiCapabilitiesFor(desired));
        return Set.copyOf(desired);
    }
}
