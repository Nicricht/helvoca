package cl.helvoca.onboarding;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.billing.MercadoPagoProperties;
import cl.helvoca.messaging.WhatsAppProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SelfServiceReadinessService {
    private final OnboardingService onboarding;
    private final BusinessSubscriptionService subscriptions;
    private final MercadoPagoProperties mercadoPago;
    private final WhatsAppProperties whatsapp;
    private final AiAgentService aiAgents;

    public SelfServiceReadinessService(OnboardingService onboarding,
                                       BusinessSubscriptionService subscriptions,
                                       MercadoPagoProperties mercadoPago,
                                       WhatsAppProperties whatsapp,
                                       AiAgentService aiAgents) {
        this.onboarding = onboarding;
        this.subscriptions = subscriptions;
        this.mercadoPago = mercadoPago;
        this.whatsapp = whatsapp;
        this.aiAgents = aiAgents;
    }

    public CommercialReadinessResponse current() {
        OnboardingStatusResponse operational = onboarding.status();
        BusinessSubscriptionService.SubscriptionView subscription = subscriptions.currentForTenant();
        boolean agentActive = aiAgents.current().isActive();

        List<String> blockers = new ArrayList<>();
        int requiredChecks = 0;
        int completedChecks = 0;

        requiredChecks++;
        if (operational.businessProfileConfigured()) completedChecks++;
        else blockers.add("BUSINESS_PROFILE_MISSING");

        if (operational.servicesRequired()) {
            requiredChecks++;
            if (operational.servicesConfigured()) completedChecks++;
            else blockers.add("SERVICES_MISSING");
        }

        if (operational.scheduleRequired()) {
            requiredChecks++;
            if (operational.scheduleConfigured()) completedChecks++;
            else blockers.add("SCHEDULE_MISSING");
        }

        requiredChecks++;
        if (operational.phoneConfigured()) completedChecks++;
        else blockers.add("PHONE_MISSING");

        requiredChecks++;
        if (subscription.serviceAllowed()) completedChecks++;
        else blockers.add("SUBSCRIPTION_BLOCKED");

        int progress = Math.max(0, Math.min(100,
                Math.round((completedChecks * 100.0f) / requiredChecks)));
        if (!agentActive) blockers.add("AI_AGENT_DISABLED");

        boolean readyForCalls = operational.readyForCalls() && subscription.serviceAllowed() && agentActive;
        boolean whatsappEnabled = whatsapp.isEnabled();
        boolean readyForWhatsApp = readyForCalls && whatsappEnabled;

        List<String> warnings = new ArrayList<>();
        if (!subscription.billingProviderConnected()) warnings.add("BILLING_NOT_CONNECTED");
        if (!mercadoPago.checkoutConfigured()) warnings.add("BILLING_CHECKOUT_NOT_CONFIGURED");
        if (!whatsappEnabled) warnings.add("WHATSAPP_DISABLED");
        if (!operational.humanTransferConfigured()) warnings.add("HUMAN_TRANSFER_NOT_CONFIGURED");
        if (!operational.knowledgeConfigured()) warnings.add("KNOWLEDGE_NOT_CONFIGURED");

        return new CommercialReadinessResponse(
                progress,
                readyForCalls,
                readyForCalls,
                readyForWhatsApp,
                subscription.publicPlanCode(),
                subscription.planName(),
                subscription.status(),
                subscription.serviceAllowed(),
                subscription.includedMinutes(),
                subscription.usedMinutes(),
                subscription.overageMinutes(),
                subscription.billingProviderConnected(),
                mercadoPago.checkoutConfigured(),
                whatsappEnabled,
                List.copyOf(blockers),
                List.copyOf(warnings));
    }
}
