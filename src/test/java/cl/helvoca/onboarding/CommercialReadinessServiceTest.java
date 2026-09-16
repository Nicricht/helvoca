package cl.helvoca.onboarding;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.billing.MercadoPagoProperties;
import cl.helvoca.messaging.WhatsAppProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialReadinessServiceTest {

    @Test
    void completeTrialTenantIsProductionReadyWithoutForcingBillingOrWhatsapp() {
        OnboardingService onboarding = mock(OnboardingService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MercadoPagoProperties mercadoPago = new MercadoPagoProperties();
        WhatsAppProperties whatsapp = new WhatsAppProperties();
        AiAgentService aiAgents = activeAgentService(true);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, false, false, true, true, "OPTIONAL_HUMAN_TRANSFER"));
        when(subscriptions.currentForTenant()).thenReturn(subscription("BASIC", "TRIALING", true, false));

        CommercialReadinessResponse result = new SelfServiceReadinessService(
                onboarding, subscriptions, mercadoPago, whatsapp, aiAgents).current();

        assertEquals(100, result.progressPercent());
        assertTrue(result.readyForProduction());
        assertTrue(result.readyForCalls());
        assertFalse(result.readyForWhatsApp());
        assertEquals("EMPRENDE", result.planCode());
        assertEquals("Emprende", result.planName());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().contains("BILLING_NOT_CONNECTED"));
        assertTrue(result.warnings().contains("BILLING_CHECKOUT_NOT_CONFIGURED"));
        assertTrue(result.warnings().contains("WHATSAPP_DISABLED"));
        assertTrue(result.warnings().contains("HUMAN_TRANSFER_NOT_CONFIGURED"));
        assertTrue(result.warnings().contains("KNOWLEDGE_NOT_CONFIGURED"));
    }

    @Test
    void missingOperationalSetupAndBlockedSubscriptionProduceExplicitBlockers() {
        OnboardingService onboarding = mock(OnboardingService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MercadoPagoProperties mercadoPago = new MercadoPagoProperties();
        WhatsAppProperties whatsapp = new WhatsAppProperties();
        AiAgentService aiAgents = activeAgentService(true);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, false, false, true, true, false, false, "ADD_SERVICE"));
        when(subscriptions.currentForTenant()).thenReturn(subscription("PRO", "SUSPENDED", false, true));

        CommercialReadinessResponse result = new SelfServiceReadinessService(
                onboarding, subscriptions, mercadoPago, whatsapp, aiAgents).current();

        assertEquals(20, result.progressPercent());
        assertFalse(result.readyForProduction());
        assertFalse(result.readyForCalls());
        assertEquals(4, result.blockers().size());
        assertTrue(result.blockers().contains("SERVICES_MISSING"));
        assertTrue(result.blockers().contains("SCHEDULE_MISSING"));
        assertTrue(result.blockers().contains("PHONE_MISSING"));
        assertTrue(result.blockers().contains("SUBSCRIPTION_BLOCKED"));
    }

    @Test
    void disabledTenantAgentBlocksCallsWithoutErasingCompletedSetupProgress() {
        OnboardingService onboarding = mock(OnboardingService.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        MercadoPagoProperties mercadoPago = new MercadoPagoProperties();
        WhatsAppProperties whatsapp = new WhatsAppProperties();
        AiAgentService aiAgents = activeAgentService(false);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, true, true, true, "READY"));
        when(subscriptions.currentForTenant()).thenReturn(subscription("BASIC", "ACTIVE", true, true));

        CommercialReadinessResponse result = new SelfServiceReadinessService(
                onboarding, subscriptions, mercadoPago, whatsapp, aiAgents).current();

        assertEquals(100, result.progressPercent());
        assertFalse(result.readyForProduction());
        assertFalse(result.readyForCalls());
        assertFalse(result.readyForWhatsApp());
        assertTrue(result.blockers().contains("AI_AGENT_DISABLED"));
    }

    private static AiAgentService activeAgentService(boolean active) {
        AiAgentService service = mock(AiAgentService.class);
        AiAgent agent = mock(AiAgent.class);
        when(agent.isActive()).thenReturn(active);
        when(service.current()).thenReturn(agent);
        return service;
    }

    private static BusinessSubscriptionService.SubscriptionView subscription(
            String plan, String status, boolean allowed, boolean billingConnected) {
        Instant now = Instant.now();
        String publicCode = "BASIC".equals(plan) ? "EMPRENDE" : "NEGOCIO";
        String planName = "BASIC".equals(plan) ? "Emprende" : "Negocio";
        return new BusinessSubscriptionService.SubscriptionView(
                UUID.randomUUID(), plan, publicCode, planName, status, allowed, 3, 300, 42, 0,
                now.minusSeconds(3600), now.plusSeconds(86400), null,
                billingConnected, List.of(), false);
    }
}
