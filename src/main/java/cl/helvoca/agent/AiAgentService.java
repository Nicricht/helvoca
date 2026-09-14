package cl.helvoca.agent;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiAgentService {
    private final AiAgentRepository agents;
    private final BusinessRepository businesses;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public AiAgentService(AiAgentRepository agents,
                          BusinessRepository businesses,
                          TenantProvider tenantProvider,
                          AuditService auditService) {
        this.agents = agents;
        this.businesses = businesses;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AiAgent current() {
        return runtime(tenantProvider.requireBusinessId());
    }

    @Transactional(readOnly = true)
    public boolean configuredCurrent() {
        return agents.existsByBusinessId(tenantProvider.requireBusinessId());
    }

    @Transactional
    public AiAgent upsert(String name,
                          String language,
                          String voice,
                          String greeting,
                          String instructions,
                          boolean active,
                          Set<AiCapability> capabilities) {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = requireBusiness(businessId);

        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(AiAgent::new);
        if (agent.getId() == null) agent.setBusinessId(businessId);
        agent.setName(defaultIfBlank(name, "RecepVoz"));
        agent.setLanguage(defaultIfBlank(language, business.getLanguage()));
        agent.setVoice(AgentVoiceProfile.normalizeForStorage(voice));
        agent.setGreeting(defaultIfBlank(greeting,
                "Hola, gracias por llamar a " + business.getName() + ". ¿En qué puedo ayudarte?"));
        agent.setInstructions(blankToNull(instructions));
        agent.setActive(active);

        // Null means "leave the capability selection as-is" for an existing
        // agent. A newly created AiAgent already starts with legacyDefaults(),
        // which deliberately excludes transactional commercial capabilities.
        if (capabilities != null) agent.setCapabilities(capabilities);

        AiAgent saved = agents.saveAndFlush(agent);
        auditService.success(businessId, "AI_AGENT_UPDATE", "AI_AGENT", saved.getId());
        return saved;
    }

    /**
     * Replaces only the opt-in commercial subset while preserving booking,
     * knowledge, request and other legacy receptionist capabilities.
     */
    @Transactional
    public AiAgent replaceCommercialCapabilities(Set<AiCapability> commercialCapabilities) {
        UUID businessId = tenantProvider.requireBusinessId();
        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(() -> defaultAgent(businessId));

        EnumSet<AiCapability> next = agent.getCapabilities() == null || agent.getCapabilities().isEmpty()
                ? EnumSet.noneOf(AiCapability.class)
                : EnumSet.copyOf(agent.getCapabilities());
        next.removeIf(AiCapability::isCommercialOperation);

        if (commercialCapabilities != null) {
            for (AiCapability capability : commercialCapabilities) {
                if (capability == null || !capability.isCommercialOperation()) {
                    throw new IllegalArgumentException("Only commercial operation capabilities can be replaced here");
                }
                next.add(capability);
            }
        }

        agent.setCapabilities(next);
        AiAgent saved = agents.saveAndFlush(agent);
        auditService.success(businessId, "AI_AGENT_COMMERCIAL_CAPABILITY_UPDATE", "AI_AGENT", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public AiAgent runtime(UUID businessId) {
        return agents.findByBusinessId(businessId).orElseGet(() -> defaultAgent(businessId));
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(UUID businessId) {
        return agents.existsByBusinessId(businessId);
    }

    @Transactional(readOnly = true)
    public boolean toolAllowed(UUID businessId, String toolName) {
        AiCapability capability = AiCapability.fromToolName(toolName).orElse(null);
        if (capability == null) return true;
        AiAgent agent = runtime(businessId);
        return agent.isActive() && agent.getCapabilities().contains(capability);
    }

    @Transactional(readOnly = true)
    public Set<String> allowedToolNames(UUID businessId) {
        AiAgent agent = runtime(businessId);
        if (!agent.isActive()) return Set.of();
        return agent.getCapabilities().stream()
                .map(AiCapability::toolName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private AiAgent defaultAgent(UUID businessId) {
        Business business = requireBusiness(businessId);
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("RecepVoz");
        agent.setLanguage(business.getLanguage());
        agent.setVoice(null);
        agent.setGreeting("Hola, gracias por llamar a " + business.getName() + ". ¿En qué puedo ayudarte?");
        agent.setInstructions(null);
        agent.setActive(true);
        agent.setCapabilities(AiCapability.legacyDefaults());
        return agent;
    }

    private Business requireBusiness(UUID businessId) {
        return businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
