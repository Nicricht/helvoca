package cl.helvoca.agent;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class OmnichannelCapabilityActivationStartupRunnerTest {

    @Test
    void addsSendWhatsAppCapabilityWithoutRemovingExistingCapabilities() {
        UUID businessId = UUID.randomUUID();
        AiAgentRepository agents = mock(AiAgentRepository.class);
        AiAgent agent = mock(AiAgent.class);
        when(agent.getCapabilities()).thenReturn(EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.CREATE_BOOKING));
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));

        OmnichannelCapabilityActivationStartupRunner runner =
                new OmnichannelCapabilityActivationStartupRunner(
                        true,
                        businessId.toString(),
                        mock(TenantDatabaseContext.class),
                        mock(PlatformTransactionManager.class),
                        agents);

        var result = runner.activate(businessId);

        assertTrue(result.changed());
        assertTrue(result.enabled());
        verify(agent).setCapabilities(argThat(capabilities ->
                capabilities.contains(AiCapability.GET_BUSINESS_INFORMATION)
                        && capabilities.contains(AiCapability.CREATE_BOOKING)
                        && capabilities.contains(AiCapability.SEND_WHATSAPP_OPERATION)));
        verify(agents).saveAndFlush(agent);
    }
}
