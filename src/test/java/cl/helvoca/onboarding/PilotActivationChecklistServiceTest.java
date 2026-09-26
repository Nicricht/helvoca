package cl.helvoca.onboarding;

import cl.helvoca.operations.PilotReadinessService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotActivationChecklistServiceTest {
    @Mock PilotActivationConfirmationRepository confirmations;
    @Mock OnboardingService onboarding;
    @Mock PilotReadinessService pilotReadiness;
    @Mock TenantProvider tenantProvider;

    private PilotActivationChecklistService service;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        service = new PilotActivationChecklistService(
                confirmations, onboarding, pilotReadiness, tenantProvider);
    }

    @Test
    void readyRequiresAutomaticSetupAndAllRequiredHumanConfirmations() {
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, true, true, true, "READY"));
        when(pilotReadiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(confirmations.findById(businessId)).thenReturn(Optional.empty());
        when(confirmations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PilotActivationChecklistService.View result = service.update(
                new PilotActivationChecklistService.ConfirmationRequest(
                        true, true, true, true, true, true, true, true));

        assertTrue(result.ready());
        assertEquals(10, result.completed());
        assertEquals(10, result.total());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.steps().stream().allMatch(PilotActivationChecklistService.Step::complete));
    }

    @Test
    void humanHandoffTestIsOptionalWhenTransferIsNotConfigured() {
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, false, true, true, "OPTIONAL_HUMAN_TRANSFER"));
        when(pilotReadiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(confirmations.findById(businessId)).thenReturn(Optional.empty());
        when(confirmations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PilotActivationChecklistService.View result = service.update(
                new PilotActivationChecklistService.ConfirmationRequest(
                        true, true, true, true, true, true, true, false));

        assertTrue(result.ready());
        assertEquals(9, result.completed());
        assertEquals(9, result.total());
        assertFalse(result.blockers().contains("HUMAN_HANDOFF_TEST_REQUIRED"));
    }

    @Test
    void exposesConcreteBlockersForExternalPilotActivation() {
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, true, true, true, "READY"));
        when(pilotReadiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                false, 4, 5, List.of(), List.of("Mercado Pago"), "gemini", "mercadopago", "SANDBOX"));

        PilotActivationConfirmation saved = new PilotActivationConfirmation();
        saved.setBusinessId(businessId);
        saved.setPricesConfirmed(true);
        saved.setFaqReviewed(false);
        saved.setPoliciesApproved(true);
        saved.setAgentInstructionsApproved(true);
        saved.setPilotScopeApproved(true);
        saved.setConversationTestCompleted(false);
        saved.setMutationTestsCompleted(true);
        saved.setHumanHandoffTested(false);
        when(confirmations.findById(businessId)).thenReturn(Optional.of(saved));

        PilotActivationChecklistService.View result = service.current();

        assertFalse(result.ready());
        assertTrue(result.blockers().contains("TECHNICAL_READINESS_BLOCKED"));
        assertTrue(result.blockers().contains("FAQ_REVIEW_REQUIRED"));
        assertTrue(result.blockers().contains("CONVERSATION_TEST_REQUIRED"));
        assertTrue(result.blockers().contains("HUMAN_HANDOFF_TEST_REQUIRED"));
    }
}
