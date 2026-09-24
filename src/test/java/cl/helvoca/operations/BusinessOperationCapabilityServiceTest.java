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
    @Mock OperationPolicyService policies;

    @Test
    void deliveryIsStandaloneAndDoesNotForceOrderOrCatalog() {
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.DELIVERY));

        assertEquals(Set.of(BusinessOperationCapability.DELIVERY), result);
        verify(aiAgents).replaceCommercialCapabilities(argThat(actual -> actual.equals(Set.of(
                AiCapability.LIST_DELIVERY_ZONES,
                AiCapability.VALIDATE_DELIVERY_ADDRESS,
                AiCapability.QUOTE_DELIVERY,
                AiCapability.UPDATE_DELIVERY,
                AiCapability.CREATE_DELIVERY,
                AiCapability.GET_DELIVERY_STATUS,
                AiCapability.CANCEL_DELIVERY))));
    }

    @Test
    void paymentIsStandaloneExplicitOptInAndDoesNotForceOtherCapabilities() {
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.PAYMENT));

        assertEquals(Set.of(BusinessOperationCapability.PAYMENT), result);
        verify(aiAgents).replaceCommercialCapabilities(argThat(actual -> actual.equals(Set.of(
                AiCapability.QUOTE_PAYMENT,
                AiCapability.UPDATE_PAYMENT,
                AiCapability.CREATE_PAYMENT,
                AiCapability.GET_PAYMENT_STATUS,
                AiCapability.CANCEL_PAYMENT))));
    }

    @Test
    void quoteAutomaticallyEnablesCatalogButNotOrder() {
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
                "update_order",
                "create_order",
                "validate_delivery_address",
                "quote_delivery",
                "quote_payment"));

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);
        Set<String> tools = service.allowedToolNames(businessId);

        assertEquals(Set.of(
                "quote_order",
                "update_order",
                "create_order",
                "validate_delivery_address",
                "quote_delivery",
                "quote_payment"), tools);
        assertFalse(tools.contains("list_services"));
    }

    @Test
    void catalogPermissionAlsoDerivesAuthoritativeShowcaseSelectionTool() {
        UUID businessId = UUID.randomUUID();
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of("list_catalog"));
        when(aiAgents.toolAllowed(businessId, "list_catalog")).thenReturn(true);

        BusinessOperationCapabilityService service =
                new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<String> tools = service.allowedToolNames(businessId);

        assertEquals(Set.of(
                "list_catalog",
                CommercialOperationToolService.SHOWCASE_SELECTION_TOOL), tools);
        assertTrue(service.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_SELECTION_TOOL));
        assertTrue(CommercialToolDefinitions.allowed(tools).toString()
                .contains(CommercialOperationToolService.SHOWCASE_SELECTION_TOOL));
    }

    @Test
    void selectedProductQuoteRequiresCatalogAndQuoteCapabilities() {
        UUID businessId = UUID.randomUUID();
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of("list_catalog", "create_quote"));
        when(aiAgents.toolAllowed(businessId, "list_catalog")).thenReturn(true);
        when(aiAgents.toolAllowed(businessId, "create_quote")).thenReturn(true);

        BusinessOperationCapabilityService service =
                new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        Set<String> tools = service.allowedToolNames(businessId);

        assertTrue(tools.contains(CommercialOperationToolService.SHOWCASE_QUOTE_TOOL));
        assertTrue(service.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_QUOTE_TOOL));
        assertTrue(CommercialToolDefinitions.allowed(tools).toString()
                .contains(CommercialOperationToolService.SHOWCASE_QUOTE_TOOL));

        when(aiAgents.toolAllowed(businessId, "create_quote")).thenReturn(false);
        assertFalse(service.isToolAllowed(
                businessId, CommercialOperationToolService.SHOWCASE_QUOTE_TOOL));
    }

    @Test
    void disabledAutomationHidesMutatingToolsButKeepsReadOnlyStatusTools() {
        UUID businessId = UUID.randomUUID();
        when(aiAgents.allowedToolNames(businessId)).thenReturn(Set.of(
                "quote_order",
                "create_order",
                "get_order_status",
                "list_catalog"));
        when(policies.allowsAutomaticExecution(businessId, BusinessOperation.Type.ORDER)).thenReturn(false);

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(
                aiAgents, tenantProvider, policies);
        Set<String> tools = service.allowedToolNames(businessId);

        assertEquals(Set.of("get_order_status", "list_catalog"), tools);
        assertFalse(service.isToolAllowed(businessId, "create_order"));
    }

    @Test
    void automationFirstDefaultAllowsMutatingToolWhenPolicyAllowsIt() {
        UUID businessId = UUID.randomUUID();
        when(aiAgents.toolAllowed(businessId, "create_payment")).thenReturn(true);
        when(policies.allowsAutomaticExecution(businessId, BusinessOperation.Type.PAYMENT)).thenReturn(true);

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(
                aiAgents, tenantProvider, policies);

        assertTrue(service.isToolAllowed(businessId, "create_payment"));
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

    @Test
    void highLevelDeliveryRequiresItsCompleteStandaloneToolSet() {
        UUID businessId = UUID.randomUUID();
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setActive(true);
        agent.setCapabilities(Set.of(
                AiCapability.LIST_DELIVERY_ZONES,
                AiCapability.VALIDATE_DELIVERY_ADDRESS));
        when(aiAgents.runtime(businessId)).thenReturn(agent);

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        assertFalse(service.enabled(businessId).contains(BusinessOperationCapability.DELIVERY));
        assertFalse(service.isEnabled(businessId, BusinessOperationCapability.DELIVERY));
    }

    @Test
    void highLevelPaymentRequiresItsCompleteToolSet() {
        UUID businessId = UUID.randomUUID();
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setActive(true);
        agent.setCapabilities(Set.of(AiCapability.QUOTE_PAYMENT, AiCapability.CREATE_PAYMENT));
        when(aiAgents.runtime(businessId)).thenReturn(agent);

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(aiAgents, tenantProvider);

        assertFalse(service.enabled(businessId).contains(BusinessOperationCapability.PAYMENT));
        assertFalse(service.isEnabled(businessId, BusinessOperationCapability.PAYMENT));
    }
}
