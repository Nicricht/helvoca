package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessOperationCapabilityServiceTest {
    @Mock AiAgentService aiAgents;
    @Mock TenantProvider tenantProvider;

    @Test
    void deliveryAutomaticallyEnablesOrderAndCatalog() {
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.DELIVERY));

        assertEquals(Set.of(
                BusinessOperationCapability.DELIVERY,
                BusinessOperationCapability.ORDER,
                BusinessOperationCapability.CATALOG), result);
        verify(aiAgents).replaceCommercialCapabilities(argThat(actual -> actual.equals(Set.of(
                AiCapability.LIST_CATALOG,
                AiCapability.QUOTE_ORDER,
                AiCapability.CREATE_ORDER,
                AiCapability.GET_ORDER_STATUS,
                AiCapability.CANCEL_ORDER,
                AiCapability.LIST_DELIVERY_ZONES,
                AiCapability.VALIDATE_DELIVERY_ADDRESS))));
    }

    @Test
    void quoteAutomaticallyEnablesCatalogButNotOrder() {
        when(tenantProvider.requireBusinessId()).thenReturn(UUID.randomUUID());
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.QUOTE));

        assertEquals(Set.of(BusinessOperationCapability.QUOTE, BusinessOperationCapability.CATALOG), result);
        verify(aiAgents).replaceCommercialCapabilities(argThat(actual -> actual.equals(Set.of(
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_QUOTE))));
    }

    @Test
    void allowedToolsAreFilteredFromTheAiAgentAuthority() {
        UUID businessId = UUID.randomUUID();
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of(
                "list_services",
                "quote_order",
                "create_order",
                "validate_delivery_address"));

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);
        Set<String> tools = service.allowedToolNames(businessId);

        assertEquals(Set.of("quote_order", "create_order", "validate_delivery_address"), tools);
        assertFalse(tools.contains("list_services"));
    }

    @Test
    void highLevelOrderIsEnabledOnlyWhenItsCompleteToolSetIsGranted() {
        UUID businessId = UUID.randomUUID();
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setActive(true);
        agent.setCapabilities(Set.of(AiCapability.CREATE_ORDER));
        when(aiAgents.runtime(businessId)).thenReturn(agent);

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        assertFalse(service.enabled(businessId).contains(BusinessOperationCapability.ORDER));
        assertFalse(service.isEnabled(businessId, BusinessOperationCapability.ORDER));
        assertTrue(agent.getCapabilities().contains(AiCapability.CREATE_ORDER));
    }
}
