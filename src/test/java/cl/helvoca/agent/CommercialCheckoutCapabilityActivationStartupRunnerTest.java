package cl.helvoca.agent;

import cl.helvoca.operations.BusinessOperationCapability;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialCheckoutCapabilityActivationStartupRunnerTest {

    @Test
    void activationAddsCheckoutCapabilitiesWithoutRemovingExistingOnes() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent agent = mock(AiAgent.class);

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));
        when(agent.getCapabilities()).thenReturn(EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.CREATE_REQUEST));

        CommercialCheckoutCapabilityActivationStartupRunner runner =
                new CommercialCheckoutCapabilityActivationStartupRunner(
                        false,
                        "",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        agents);

        var result = runner.activate(businessId);

        assertTrue(result.changed());
        assertTrue(result.catalog());
        assertTrue(result.quote());
        assertTrue(result.order());
        assertTrue(result.payment());
        assertTrue(result.whatsapp());
        verify(agent).setCapabilities(argThat(capabilities ->
                capabilities.contains(AiCapability.GET_BUSINESS_INFORMATION)
                        && capabilities.contains(AiCapability.CREATE_REQUEST)
                        && capabilities.contains(AiCapability.LIST_CATALOG)
                        && capabilities.contains(AiCapability.CREATE_QUOTE)
                        && capabilities.containsAll(BusinessOperationCapability.ORDER.aiCapabilities())
                        && capabilities.containsAll(BusinessOperationCapability.PAYMENT.aiCapabilities())
                        && capabilities.contains(AiCapability.SEND_WHATSAPP_OPERATION)));
        verify(agents).saveAndFlush(agent);
    }

    @Test
    void activationIsIdempotentWhenCheckoutAlreadyEnabled() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent agent = mock(AiAgent.class);

        EnumSet<AiCapability> capabilities = EnumSet.of(
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_QUOTE,
                AiCapability.SEND_WHATSAPP_OPERATION);
        capabilities.addAll(BusinessOperationCapability.ORDER.aiCapabilities());
        capabilities.addAll(BusinessOperationCapability.PAYMENT.aiCapabilities());

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));
        when(agent.getCapabilities()).thenReturn(capabilities);

        CommercialCheckoutCapabilityActivationStartupRunner runner =
                new CommercialCheckoutCapabilityActivationStartupRunner(
                        false,
                        "",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        agents);

        var result = runner.activate(businessId);

        assertFalse(result.changed());
        assertTrue(result.order());
        assertTrue(result.payment());
        verify(agent, never()).setCapabilities(any());
        verify(agents, never()).saveAndFlush(any());
    }
}
