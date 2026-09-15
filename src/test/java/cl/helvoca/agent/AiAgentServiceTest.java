package cl.helvoca.agent;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.OperationPolicyService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        assertEquals(AiCapability.legacyDefaults(), runtime.getCapabilities());
        assertTrue(runtime.getGreeting().contains("Clínica Norte"));
        assertTrue(service.toolAllowed(businessId, "create_booking"));
        assertFalse(service.toolAllowed(businessId, "create_order"));
        assertFalse(service.toolAllowed(businessId, "list_catalog"));
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
        assertFalse(service.toolAllowed(businessId, "create_order"));
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
        assertFalse(service.toolAllowed(businessId, "create_order"));
    }

    @Test
    void automationPolicyCanPauseMutationsAcrossBookingRequestAndCommercialTools() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        OperationPolicyService policies = mock(OperationPolicyService.class);
        AiAgent configured = new AiAgent();
        configured.setBusinessId(businessId);
        configured.setName("Luna");
        configured.setLanguage("es");
        configured.setGreeting("Hola");
        configured.setActive(true);
        configured.setCapabilities(Set.of(
                AiCapability.LIST_AVAILABLE_SLOTS,
                AiCapability.CREATE_BOOKING,
                AiCapability.CREATE_REQUEST,
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_ORDER,
                AiCapability.GET_ORDER_STATUS));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(configured));
        when(policies.allowsAutomaticExecution(businessId, BusinessOperation.Type.BOOKING)).thenReturn(false);
        when(policies.allowsAutomaticExecution(businessId, BusinessOperation.Type.REQUEST)).thenReturn(false);
        when(policies.allowsAutomaticExecution(businessId, BusinessOperation.Type.ORDER)).thenReturn(false);

        AiAgentService service = new AiAgentService(
                agents,
                mock(BusinessRepository.class),
                mock(TenantProvider.class),
                mock(AuditService.class),
                policies);

        Set<String> tools = service.allowedToolNames(businessId);

        assertTrue(tools.contains("list_available_slots"));
        assertTrue(tools.contains("list_catalog"));
        assertTrue(tools.contains("get_order_status"));
        assertFalse(tools.contains("create_booking"));
        assertFalse(tools.contains("create_request"));
        assertFalse(tools.contains("create_order"));
    }

    @Test
    void replacingCommercialCapabilitiesPreservesLegacySelection() {
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
        configured.setCapabilities(Set.of(
                AiCapability.LIST_SERVICES,
                AiCapability.CREATE_BOOKING,
                AiCapability.CREATE_LEAD));

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(configured));
        when(agents.saveAndFlush(any(AiAgent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiAgentService service = new AiAgentService(agents, businesses, tenant, audit);
        AiAgent saved = service.replaceCommercialCapabilities(Set.of(
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_ORDER));

        assertTrue(saved.getCapabilities().contains(AiCapability.LIST_SERVICES));
        assertTrue(saved.getCapabilities().contains(AiCapability.CREATE_BOOKING));
        assertTrue(saved.getCapabilities().contains(AiCapability.LIST_CATALOG));
        assertTrue(saved.getCapabilities().contains(AiCapability.CREATE_ORDER));
        assertFalse(saved.getCapabilities().contains(AiCapability.CREATE_LEAD));
        verify(audit).success(eq(businessId), eq("AI_AGENT_COMMERCIAL_CAPABILITY_UPDATE"), eq("AI_AGENT"), any());
    }
}
