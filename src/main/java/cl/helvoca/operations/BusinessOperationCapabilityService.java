package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BusinessOperationCapabilityService {
    private final AiAgentService aiAgents;
    private final TenantProvider tenantProvider;
    private final OperationPolicyService policies;

    public BusinessOperationCapabilityService(AiAgentService aiAgents,
                                              TenantProvider tenantProvider) {
        this(aiAgents, tenantProvider, null);
    }

    @Autowired
    public BusinessOperationCapabilityService(AiAgentService aiAgents,
                                              TenantProvider tenantProvider,
                                              OperationPolicyService policies) {
        this.aiAgents = aiAgents;
        this.tenantProvider = tenantProvider;
        this.policies = policies;
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
        LinkedHashSet<String> allowed = aiAgents.allowedToolNames(businessId).stream()
                .filter(toolName -> CrossChannelMessagingToolService.TOOL_NAME.equals(toolName)
                        || BusinessOperationCapability.isCommercialToolName(toolName))
                .filter(toolName -> automationAllowsTool(businessId, toolName))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Product selection is a derived catalog action, not a separately
        // persisted tenant grant. Any tenant that can list its catalog may
        // resolve a selection only against backend-owned showcase state.
        if (aiAgents.toolAllowed(businessId, "list_catalog")
                && automationAllowsTool(businessId, CommercialOperationToolService.SHOWCASE_SELECTION_TOOL)) {
            allowed.add(CommercialOperationToolService.SHOWCASE_SELECTION_TOOL);
        }
        return Set.copyOf(allowed);
    }

    @Transactional(readOnly = true)
    public boolean isToolAllowed(UUID businessId, String toolName) {
        if (CrossChannelMessagingToolService.TOOL_NAME.equals(toolName)) {
            return aiAgents.toolAllowed(businessId, toolName);
        }
        if (CommercialOperationToolService.SHOWCASE_SELECTION_TOOL.equals(toolName)) {
            return aiAgents.toolAllowed(businessId, "list_catalog")
                    && automationAllowsTool(businessId, toolName);
        }
        return BusinessOperationCapability.isCommercialToolName(toolName)
                && aiAgents.toolAllowed(businessId, toolName)
                && automationAllowsTool(businessId, toolName);
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
        // and QUOTE depend on the catalog; DELIVERY is an autonomous domain.
        if (desired.contains(BusinessOperationCapability.ORDER)
                || desired.contains(BusinessOperationCapability.QUOTE)) {
            desired.add(BusinessOperationCapability.CATALOG);
        }

        aiAgents.replaceCommercialCapabilities(BusinessOperationCapability.aiCapabilitiesFor(desired));
        return Set.copyOf(desired);
    }

    /**
     * Read-only discovery/status tools remain available even if automatic
     * mutation is disabled. Mutating tools are hidden and rejected consistently
     * by the same capability authority used by voice and WhatsApp.
     */
    private boolean automationAllowsTool(UUID businessId, String toolName) {
        if (policies == null || businessId == null || toolName == null) return true;
        BusinessOperation.Type type = mutatingOperationType(toolName);
        return type == null || policies.allowsAutomaticExecution(businessId, type);
    }

    private static BusinessOperation.Type mutatingOperationType(String toolName) {
        return switch (toolName) {
            case "quote_order", "update_order", "create_order", "cancel_order" -> BusinessOperation.Type.ORDER;
            case "quote_delivery", "update_delivery", "create_delivery", "cancel_delivery" -> BusinessOperation.Type.DELIVERY;
            case "create_quote" -> BusinessOperation.Type.QUOTE;
            case "create_lead" -> BusinessOperation.Type.LEAD;
            case CommercialOperationToolService.SHOWCASE_SELECTION_TOOL -> BusinessOperation.Type.REQUEST;
            case "quote_payment", "update_payment", "create_payment", "cancel_payment" -> BusinessOperation.Type.PAYMENT;
            default -> null;
        };
    }
}
