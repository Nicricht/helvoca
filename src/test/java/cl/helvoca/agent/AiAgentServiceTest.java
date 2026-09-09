package cl.helvoca.agent;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiAgentServiceTest {
    @Test
    void upsertUsesAuthenticatedTenantAndCapabilities() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        Business business = new Business();
        business.setName("Restaurante Norte");
        business.setLanguage("es");
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(agents.saveAndFlush(any(AiAgent.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAgentService service = new AiAgentService(agents, businesses, tenant, audit);
        AiAgent result = service.upsert(
                "Luna", "es", "marin", "Bienvenido", "No uses tecnicismos", true,
                EnumSet.of(AiCapability.LIST_SERVICES, AiCapability.SEARCH_KNOWLEDGE));

        assertEquals(businessId, result.getBusinessId());
        assertEquals("Luna", result.getName());
        assertEquals(EnumSet.of(AiCapability.LIST_SERVICES, AiCapability.SEARCH_KNOWLEDGE), result.getCapabilities());
        verify(agents).saveAndFlush(any(AiAgent.class));
    }

    @Test
    void nullCapabilitiesDefaultToAll() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        Business business = new Business();
        business.setName("Clínica Helvoca");
        business.setLanguage("es");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(agents.saveAndFlush(any(AiAgent.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAgent result = new AiAgentService(agents, businesses, tenant, audit)
                .upsert("Helvoca", null, null, null, null, true, null);

        assertEquals(EnumSet.allOf(AiCapability.class), result.getCapabilities());
        assertEquals("marin", result.getVoice());
        assertTrue(result.getGreeting().contains("Clínica Helvoca"));
    }
}
