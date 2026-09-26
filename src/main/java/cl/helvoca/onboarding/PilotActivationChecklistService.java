package cl.helvoca.onboarding;

import cl.helvoca.operations.PilotReadinessService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PilotActivationChecklistService {
    private final PilotActivationConfirmationRepository confirmations;
    private final OnboardingService onboarding;
    private final PilotReadinessService pilotReadiness;
    private final TenantProvider tenantProvider;

    public PilotActivationChecklistService(PilotActivationConfirmationRepository confirmations,
                                           OnboardingService onboarding,
                                           PilotReadinessService pilotReadiness,
                                           TenantProvider tenantProvider) {
        this.confirmations = confirmations;
        this.onboarding = onboarding;
        this.pilotReadiness = pilotReadiness;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public View current() {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotActivationConfirmation confirmation = confirmations.findById(businessId)
                .orElseGet(() -> empty(businessId));
        return view(confirmation, onboarding.status(), pilotReadiness.readiness());
    }

    @Transactional
    public View update(ConfirmationRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        PilotActivationConfirmation confirmation = confirmations.findById(businessId)
                .orElseGet(() -> empty(businessId));

        confirmation.setPricesConfirmed(request != null && request.pricesConfirmed());
        confirmation.setFaqReviewed(request != null && request.faqReviewed());
        confirmation.setPoliciesApproved(request != null && request.policiesApproved());
        confirmation.setAgentInstructionsApproved(request != null && request.agentInstructionsApproved());
        confirmation.setPilotScopeApproved(request != null && request.pilotScopeApproved());
        confirmation.setConversationTestCompleted(request != null && request.conversationTestCompleted());
        confirmation.setMutationTestsCompleted(request != null && request.mutationTestsCompleted());
        confirmation.setHumanHandoffTested(request != null && request.humanHandoffTested());

        PilotActivationConfirmation saved = confirmations.save(confirmation);
        return view(saved, onboarding.status(), pilotReadiness.readiness());
    }

    private static PilotActivationConfirmation empty(UUID businessId) {
        PilotActivationConfirmation confirmation = new PilotActivationConfirmation();
        confirmation.setBusinessId(businessId);
        return confirmation;
    }

    private static View view(PilotActivationConfirmation confirmation,
                             OnboardingStatusResponse onboarding,
                             PilotReadinessService.Readiness readiness) {
        List<Step> steps = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        boolean coreSetup = onboarding != null && onboarding.readyForCalls();
        boolean technical = readiness != null && readiness.ready();
        boolean requireHandoff = onboarding != null && onboarding.humanTransferConfigured();

        add(steps, blockers, "CORE_SETUP", "Negocio configurado", coreSetup, true, true,
                "CORE_SETUP_INCOMPLETE", "/settings.html#configuration");
        add(steps, blockers, "TECHNICAL_READINESS", "Canales y operación listos", technical, true, true,
                "TECHNICAL_READINESS_BLOCKED", "/");
        add(steps, blockers, "PRICES_CONFIRMED", "Precios confirmados por el negocio",
                confirmation.isPricesConfirmed(), false, true, "PRICES_CONFIRMATION_REQUIRED", "/settings.html#configuration");
        add(steps, blockers, "FAQ_REVIEWED", "FAQ revisada y aprobada",
                confirmation.isFaqReviewed(), false, true, "FAQ_REVIEW_REQUIRED", "/settings.html#configuration");
        add(steps, blockers, "POLICIES_APPROVED", "Políticas del negocio aprobadas",
                confirmation.isPoliciesApproved(), false, true, "POLICIES_APPROVAL_REQUIRED", "/settings.html#configuration");
        add(steps, blockers, "AGENT_INSTRUCTIONS_APPROVED", "Identidad e instrucciones del agente aprobadas",
                confirmation.isAgentInstructionsApproved(), false, true, "AGENT_INSTRUCTIONS_APPROVAL_REQUIRED", "/settings.html#configuration");
        add(steps, blockers, "PILOT_SCOPE_APPROVED", "Alcance y límites del piloto entendidos",
                confirmation.isPilotScopeApproved(), false, true, "PILOT_SCOPE_APPROVAL_REQUIRED", "/settings.html");
        add(steps, blockers, "CONVERSATION_TEST_COMPLETED", "Conversación real de prueba completada",
                confirmation.isConversationTestCompleted(), false, true, "CONVERSATION_TEST_REQUIRED", "/");
        add(steps, blockers, "MUTATION_TESTS_COMPLETED", "Reservas/pedidos/pagos probados",
                confirmation.isMutationTestsCompleted(), false, true, "MUTATION_TESTS_REQUIRED", "/");
        if (requireHandoff) {
            add(steps, blockers, "HUMAN_HANDOFF_TESTED", "Derivación humana probada",
                    confirmation.isHumanHandoffTested(), false, true, "HUMAN_HANDOFF_TEST_REQUIRED", "/settings.html#configuration");
        }

        long completed = steps.stream().filter(Step::complete).count();
        long total = steps.stream().filter(Step::required).count();

        return new View(
                completed == total,
                completed,
                total,
                total == 0 ? 0 : (int) Math.round(completed * 100.0 / total),
                List.copyOf(blockers),
                List.copyOf(steps));
    }

    private static void add(List<Step> steps,
                            List<String> blockers,
                            String code,
                            String label,
                            boolean complete,
                            boolean automatic,
                            boolean required,
                            String blocker,
                            String actionHref) {
        steps.add(new Step(code, label, complete, automatic, required, actionHref));
        if (required && !complete && blocker != null) blockers.add(blocker);
    }

    public record ConfirmationRequest(
            boolean pricesConfirmed,
            boolean faqReviewed,
            boolean policiesApproved,
            boolean agentInstructionsApproved,
            boolean pilotScopeApproved,
            boolean conversationTestCompleted,
            boolean mutationTestsCompleted,
            boolean humanHandoffTested) {}

    public record Step(
            String code,
            String label,
            boolean complete,
            boolean automatic,
            boolean required,
            String actionHref) {}

    public record View(
            boolean ready,
            long completed,
            long total,
            int progressPercent,
            List<String> blockers,
            List<Step> steps) {}
}
