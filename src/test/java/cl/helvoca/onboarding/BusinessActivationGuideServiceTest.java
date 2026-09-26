package cl.helvoca.onboarding;

import cl.helvoca.operations.PilotLaunchControlService;
import cl.helvoca.operations.PilotReadinessService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessActivationGuideServiceTest {

    @Test
    void newBusinessGetsOneOrderedPathFromAccountToPilot() {
        OnboardingService onboarding = mock(OnboardingService.class);
        PilotReadinessService readiness = mock(PilotReadinessService.class);
        PilotActivationChecklistService activation = mock(PilotActivationChecklistService.class);
        PilotLaunchControlService pilot = mock(PilotLaunchControlService.class);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                false, false, false, false, false, false, false, "BUSINESS_PROFILE"));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                false, 0, 5, List.of(
                        new PilotReadinessService.Check("VOICE", "Llamadas con IA", false, "Pendiente"),
                        new PilotReadinessService.Check("WHATSAPP", "WhatsApp bidireccional", false, "Pendiente"),
                        new PilotReadinessService.Check("CATALOG", "Catálogo multimedia", false, "Pendiente"),
                        new PilotReadinessService.Check("COMMERCIAL_FLOW", "Venta conversacional", false, "Pendiente"),
                        new PilotReadinessService.Check("PAYMENTS", "Mercado Pago", false, "Pendiente")),
                List.of("Llamadas con IA", "WhatsApp bidireccional", "Catálogo multimedia", "Venta conversacional", "Mercado Pago"),
                null, "mercadopago", "SANDBOX"));
        when(activation.current()).thenReturn(new PilotActivationChecklistService.View(
                false, 0, 10, 0, List.of("CORE_SETUP_INCOMPLETE"), List.of()));
        when(pilot.current()).thenReturn(pilotView("DRAFT", "NO_GO", false));

        BusinessActivationGuideService service =
                new BusinessActivationGuideService(onboarding, readiness, activation, pilot);

        BusinessActivationGuideService.Guide result = service.current();

        assertFalse(result.readyForPilot());
        assertEquals(7, result.total());
        assertEquals(1, result.completed());
        assertEquals("BUSINESS_SETUP", result.nextStep().code());
        assertEquals("/#aiForm", result.nextStep().actionHref());
        assertEquals(List.of(
                "ACCOUNT",
                "BUSINESS_SETUP",
                "PHONE",
                "WHATSAPP",
                "COMMERCIAL",
                "ACTIVATION",
                "PILOT"),
                result.steps().stream().map(BusinessActivationGuideService.Step::code).toList());
    }

    @Test
    void completeOnboardingEndsAtPilotControlWithoutTechnicalInstructions() {
        OnboardingService onboarding = mock(OnboardingService.class);
        PilotReadinessService readiness = mock(PilotReadinessService.class);
        PilotActivationChecklistService activation = mock(PilotActivationChecklistService.class);
        PilotLaunchControlService pilot = mock(PilotLaunchControlService.class);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, true, true, true, "READY"));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(
                        new PilotReadinessService.Check("VOICE", "Llamadas con IA", true, "OK"),
                        new PilotReadinessService.Check("WHATSAPP", "WhatsApp bidireccional", true, "OK"),
                        new PilotReadinessService.Check("CATALOG", "Catálogo multimedia", true, "OK"),
                        new PilotReadinessService.Check("COMMERCIAL_FLOW", "Venta conversacional", true, "OK"),
                        new PilotReadinessService.Check("PAYMENTS", "Mercado Pago", true, "OK")),
                List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(activation.current()).thenReturn(new PilotActivationChecklistService.View(
                true, 10, 10, 100, List.of(), List.of()));
        when(pilot.current()).thenReturn(pilotView("READY", "GO", true));

        BusinessActivationGuideService service =
                new BusinessActivationGuideService(onboarding, readiness, activation, pilot);

        BusinessActivationGuideService.Guide result = service.current();

        assertTrue(result.readyForPilot());
        assertEquals(7, result.completed());
        assertEquals(100, result.progressPercent());
        assertNull(result.nextStep());
        assertTrue(result.steps().stream().allMatch(BusinessActivationGuideService.Step::complete));
    }

    @Test
    void choosesChannelsBeforeCommerceWhenCoreBusinessSetupAlreadyExists() {
        OnboardingService onboarding = mock(OnboardingService.class);
        PilotReadinessService readiness = mock(PilotReadinessService.class);
        PilotActivationChecklistService activation = mock(PilotActivationChecklistService.class);
        PilotLaunchControlService pilot = mock(PilotLaunchControlService.class);

        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, true, false, true, true, "OPTIONAL_HUMAN_TRANSFER"));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                false, 2, 5, List.of(
                        new PilotReadinessService.Check("VOICE", "Llamadas con IA", true, "OK"),
                        new PilotReadinessService.Check("WHATSAPP", "WhatsApp bidireccional", false, "Pendiente"),
                        new PilotReadinessService.Check("CATALOG", "Catálogo multimedia", false, "Pendiente"),
                        new PilotReadinessService.Check("COMMERCIAL_FLOW", "Venta conversacional", false, "Pendiente"),
                        new PilotReadinessService.Check("PAYMENTS", "Mercado Pago", true, "OK")),
                List.of("WhatsApp bidireccional", "Catálogo multimedia", "Venta conversacional"),
                "gemini", "mercadopago", "SANDBOX"));
        when(activation.current()).thenReturn(new PilotActivationChecklistService.View(
                false, 2, 9, 22, List.of("TECHNICAL_READINESS_BLOCKED"), List.of()));
        when(pilot.current()).thenReturn(pilotView("DRAFT", "NO_GO", false));

        BusinessActivationGuideService service =
                new BusinessActivationGuideService(onboarding, readiness, activation, pilot);

        BusinessActivationGuideService.Guide result = service.current();

        assertEquals("WHATSAPP", result.nextStep().code());
        assertEquals("/settings.html#configuration", result.nextStep().actionHref());
        assertEquals("Conectar WhatsApp", result.nextStep().actionLabel());
    }

    private static PilotLaunchControlService.View pilotView(
            String status, String decision, boolean canStart) {
        return new PilotLaunchControlService.View(
                status,
                decision,
                true,
                true,
                List.of(),
                "Responsable",
                "contacto@example.com",
                "Validar piloto",
                java.time.Instant.parse("2026-10-10T03:00:00Z"),
                null,
                null,
                canStart,
                false,
                false,
                false);
    }
}
