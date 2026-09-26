package cl.helvoca.onboarding;

import cl.helvoca.operations.PilotLaunchControlService;
import cl.helvoca.operations.PilotReadinessService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessActivationGuideService {
    private final OnboardingService onboarding;
    private final PilotReadinessService readiness;
    private final PilotActivationChecklistService activation;
    private final PilotLaunchControlService pilotControl;

    public BusinessActivationGuideService(OnboardingService onboarding,
                                          PilotReadinessService readiness,
                                          PilotActivationChecklistService activation,
                                          PilotLaunchControlService pilotControl) {
        this.onboarding = onboarding;
        this.readiness = readiness;
        this.activation = activation;
        this.pilotControl = pilotControl;
    }

    public Guide current() {
        OnboardingStatusResponse onboardingStatus = onboarding.status();
        PilotReadinessService.Readiness pilotReadiness = readiness.readiness();
        PilotActivationChecklistService.View activationStatus = activation.current();
        PilotLaunchControlService.View pilot = pilotControl.current();

        boolean businessSetup = coreBusinessSetupComplete(onboardingStatus);
        boolean voice = onboardingStatus.phoneConfigured() && ready(pilotReadiness, "VOICE");
        boolean whatsapp = ready(pilotReadiness, "WHATSAPP");
        boolean commercial = ready(pilotReadiness, "CATALOG")
                && ready(pilotReadiness, "COMMERCIAL_FLOW")
                && ready(pilotReadiness, "PAYMENTS");
        boolean activationComplete = activationStatus.ready();
        boolean pilotConfigured = !"DRAFT".equalsIgnoreCase(pilot.status());

        List<Step> steps = new ArrayList<>();
        steps.add(step("ACCOUNT", "Cuenta y administrador", true,
                "Tu negocio y su administrador ya existen.", null, null));
        steps.add(step("BUSINESS_SETUP", "Datos, oferta y horarios", businessSetup,
                businessSetup
                        ? "La configuración base del negocio está lista."
                        : "Completa o importa los datos públicos, servicios y horarios que correspondan.",
                "Preparar negocio", "/#aiForm"));
        steps.add(step("PHONE", "Teléfono y voz", voice,
                voice
                        ? "El canal de voz está listo."
                        : "Conecta el número y completa la configuración de voz.",
                "Configurar canales", "/settings.html#configuration"));
        steps.add(step("WHATSAPP", "WhatsApp", whatsapp,
                whatsapp
                        ? "WhatsApp puede recibir y enviar mensajes."
                        : "Conecta WhatsApp Business para continuar la conversación desde la llamada.",
                "Conectar WhatsApp", "/settings.html#configuration"));
        steps.add(step("COMMERCIAL", "Catálogo, venta y pagos", commercial,
                commercial
                        ? "Catálogo y operación comercial están listos."
                        : "Completa catálogo, capacidades comerciales y Mercado Pago.",
                "Preparar venta", "/settings.html#configuration"));
        steps.add(step("ACTIVATION", "Validación del negocio", activationComplete,
                activationComplete
                        ? "El checklist de activación está completo."
                        : "Revisa con el negocio precios, FAQ, políticas y pruebas antes de activar.",
                "Completar checklist", "/settings.html#configuration"));
        steps.add(step("PILOT", "Control del piloto", pilotConfigured,
                pilotConfigured
                        ? "El piloto ya tiene responsable, objetivo y ciclo operativo."
                        : "Define responsable, objetivo y fecha de cierre para habilitar el piloto.",
                "Configurar piloto", "/#pilotControlCard"));

        long completed = steps.stream().filter(Step::complete).count();
        Step next = steps.stream().filter(item -> !item.complete()).findFirst().orElse(null);
        int progress = (int) Math.round(completed * 100.0 / steps.size());

        return new Guide(
                completed == steps.size(),
                completed,
                steps.size(),
                progress,
                next,
                List.copyOf(steps));
    }

    private static boolean coreBusinessSetupComplete(OnboardingStatusResponse status) {
        if (status == null || !status.businessProfileConfigured()) return false;
        String next = status.nextStep();
        return !"CONFIGURE_BUSINESS".equals(next)
                && !"ADD_SERVICE".equals(next)
                && !"CONFIGURE_HOURS".equals(next);
    }

    private static boolean ready(PilotReadinessService.Readiness readiness, String code) {
        if (readiness == null || readiness.checks() == null) return false;
        return readiness.checks().stream()
                .anyMatch(check -> code.equals(check.code()) && check.ready());
    }

    private static Step step(String code,
                             String label,
                             boolean complete,
                             String detail,
                             String actionLabel,
                             String actionHref) {
        return new Step(code, label, complete, detail, actionLabel, actionHref);
    }

    public record Step(
            String code,
            String label,
            boolean complete,
            String detail,
            String actionLabel,
            String actionHref) {}

    public record Guide(
            boolean readyForPilot,
            long completed,
            long total,
            int progressPercent,
            Step nextStep,
            List<Step> steps) {}
}
