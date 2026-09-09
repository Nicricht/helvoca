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
        UUID businessId = tenantProvider.requireBusinessId();
        return agents.findByBusinessId(businessId)
                .orElseThrow(() -> new NotFoundException("AI agent not found"));
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
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));

        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(AiAgent::new);
        if (agent.getId() == null) agent.setBusinessId(businessId);
        agent.setName(requireText(name, "name"));
        agent.setLanguage(defaultIfBlank(language, business.getLanguage()));
        agent.setVoice(defaultIfBlank(voice, "marin"));
        agent.setGreeting(defaultIfBlank(greeting,
                "Hola, gracias por llamar a " + business.getName() + ". ¿En qué puedo ayudarte?"));
        agent.setInstructions(blankToNull(instructions));
        agent.setActive(active);
        agent.setCapabilities(capabilities == null ? EnumSet.allOf(AiCapability.class) : capabilities);
        AiAgent saved = agents.saveAndFlush(agent);
        auditService.success(businessId, "AI_AGENT_UPDATE", "AI_AGENT", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public AiAgent runtime(UUID businessId) {
        return agents.findByBusinessId(businessId)
                .orElseGet(() -> defaultRuntime(businessId));
    }

    private AiAgent defaultRuntime(UUID businessId) {
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Helvoca");
        agent.setLanguage(business.getLanguage());
        agent.setVoice("marin");
        agent.setGreeting("Hola, gracias por llamar a " + business.getName() + ". ¿En qué puedo ayudarte?");
        agent.setActive(true);
        agent.setCapabilities(EnumSet.allOf(AiCapability.class));
        return agent;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
