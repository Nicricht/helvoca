package cl.helvoca.agent;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectedProductQuoteCapabilityActivationStartupRunnerTest {

    @Test
    void activationAddsCatalogAndQuoteWithoutRemovingExistingCapabilities() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent agent = mock(AiAgent.class);

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));
        when(agent.getCapabilities()).thenReturn(EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.SEND_WHATSAPP_OPERATION));

        SelectedProductQuoteCapabilityActivationStartupRunner runner =
                new SelectedProductQuoteCapabilityActivationStartupRunner(
                        false,
                        "",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        agents);

        var result = runner.activate(businessId);

        assertTrue(result.changed());
        assertTrue(result.listCatalog());
        assertTrue(result.createQuote());
        verify(agent).setCapabilities(argThat(capabilities ->
                capabilities.contains(AiCapability.GET_BUSINESS_INFORMATION)
                        && capabilities.contains(AiCapability.SEND_WHATSAPP_OPERATION)
                        && capabilities.contains(AiCapability.LIST_CATALOG)
                        && capabilities.contains(AiCapability.CREATE_QUOTE)));
        verify(agents).saveAndFlush(agent);
    }

    @Test
    void activationIsIdempotent() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent agent = mock(AiAgent.class);

        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));
        when(agent.getCapabilities()).thenReturn(EnumSet.of(
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_QUOTE));

        SelectedProductQuoteCapabilityActivationStartupRunner runner =
                new SelectedProductQuoteCapabilityActivationStartupRunner(
                        false,
                        "",
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        agents);

        var result = runner.activate(businessId);

        assertFalse(result.changed());
        assertTrue(result.listCatalog());
        assertTrue(result.createQuote());
        verify(agent, never()).setCapabilities(any());
        verify(agents, never()).saveAndFlush(any());
    }
}
