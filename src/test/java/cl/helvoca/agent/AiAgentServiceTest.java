package cl.helvoca.agent;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAgentServiceTest {

    @Test
    void runtimeFallsBackToSafeBackwardCompatibleDefaults() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        Business business = mock(Business.class);

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(business.getName()).thenReturn("Clínica Norte");
        when(business.getLanguage()).thenReturn("es");

        AiAgentService service = new AiAgentService(agents, businesses, tenant, audit);
        AiAgent runtime = service.runtime(businessId);

        assertEquals("RecepVoz", runtime.getName());
        assertEquals("es", runtime.getLanguage());
        assertNull(runtime.getVoice());
        assertTrue(runtime.isActive());
        assertEquals(EnumSet.allOf(AiCapability.class), runtime.getCapabilities());
        assertTrue(runtime.getGreeting().contains("Clínica Norte"));
        assertTrue(service.toolAllowed(businessId, "create_booking"));
    }

    @Test
    void explicitCapabilitiesAreFailClosedForKnownTools() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        AiAgent configured = new AiAgent();
        configured.setBusinessId(businessId);
        configured.setName("Luna");
        configured.setLanguage("es");
        configured.setGreeting("Hola");
        configured.setActive(true);
        configured.setCapabilities(Set.of(AiCapability.LIST_SERVICES));

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(configured));

        AiAgentService service = new AiAgentService(agents, businesses, tenant, audit);

        assertTrue(service.toolAllowed(businessId, "list_services"));
        assertFalse(service.toolAllowed(businessId, "create_booking"));
        assertTrue(service.toolAllowed(businessId, "unknown_future_tool"));
    }

    @Test
    void disabledAgentPublishesNoCapabilities() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent configured = new AiAgent();
        configured.setBusinessId(businessId);
        configured.setName("Luna");
        configured.setLanguage("es");
        configured.setGreeting("Hola");
        configured.setActive(false);
        configured.setCapabilities(EnumSet.allOf(AiCapability.class));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(configured));

        AiAgentService service = new AiAgentService(
                agents, mock(BusinessRepository.class), mock(TenantProvider.class), mock(AuditService.class));

        assertTrue(service.allowedToolNames(businessId).isEmpty());
        assertFalse(service.toolAllowed(businessId, "list_services"));
    }
}
