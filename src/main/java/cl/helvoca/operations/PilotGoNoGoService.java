package cl.helvoca.operations;

import cl.helvoca.inventory.InventoryAlert;
import cl.helvoca.inventory.InventoryAlertService;
import cl.helvoca.inventory.InventoryService;
import cl.helvoca.onboarding.PilotActivationChecklistService;
import cl.helvoca.reconciliation.ReconciliationAnomaly;
import cl.helvoca.reconciliation.ReconciliationService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PilotGoNoGoService {
    private final PilotReadinessService readiness;
    private final PilotActivationChecklistService activation;
    private final PilotLaunchControlService control;
    private final ControlledPilotExternalEffectGuard externalEffects;
    private final InventoryService inventory;
    private final InventoryAlertService inventoryAlerts;
    private final ReconciliationService reconciliation;
    private final PilotMetricsService metrics;
    private final TenantProvider tenantProvider;

    public PilotGoNoGoService(PilotReadinessService readiness,
                              PilotActivationChecklistService activation,
                              PilotLaunchControlService control,
                              ControlledPilotExternalEffectGuard externalEffects,
                              InventoryService inventory,
                              InventoryAlertService inventoryAlerts,
                              ReconciliationService reconciliation,
                              PilotMetricsService metrics,
                              TenantProvider tenantProvider) {
        this.readiness = readiness;
        this.activation = activation;
        this.control = control;
        this.externalEffects = externalEffects;
        this.inventory = inventory;
        this.inventoryAlerts = inventoryAlerts;
        this.reconciliation = reconciliation;
        this.metrics = metrics;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public View evaluate() {
        PilotReadinessService.Readiness technical = readiness.readiness();
        PilotActivationChecklistService.View activationView = activation.current();
        PilotLaunchControlService.View controlView = control.current();

        List<InventoryService.StockView> stocks = inventory.list();
        List<InventoryAlertService.AlertView> alerts = inventoryAlerts.listOpen();
        List<ReconciliationAnomaly> anomalies = reconciliation.detect();
        PilotMetricsService.Metrics pilotMetrics = metrics.metrics();
        var businessId = tenantProvider.requireBusinessId();

        List<Check> checks = new ArrayList<>();

        if (technical != null && technical.checks() != null) {
            technical.checks().forEach(check -> checks.add(new Check(
                    "TECH_" + check.code(),
                    check.label(),
                    check.ready(),
                    true,
                    check.detail())));
        }

        if (activationView != null && activationView.steps() != null) {
            activationView.steps().stream()
                    .filter(PilotActivationChecklistService.Step::required)
                    .forEach(step -> checks.add(new Check(
                            "ACTIVATION_" + step.code(),
                            step.label(),
                            step.complete(),
                            true,
                            step.complete() ? "Confirmado." : "Debe completarse antes del piloto.")));
        }

        boolean configured = controlView != null && controlView.configurationComplete();
        checks.add(new Check(
                "PILOT_CONFIGURATION",
                "Responsable, objetivo y cierre configurados",
                configured,
                true,
                configured ? "Configuración operativa completa." : "Completa responsable, contacto, objetivo y fecha de cierre."));

        String status = controlView == null ? "DRAFT" : controlView.status();
        boolean lifecycleSafe = !"COMPLETED".equals(status);
        checks.add(new Check(
                "PILOT_LIFECYCLE",
                "Ciclo de vida disponible",
                lifecycleSafe,
                true,
                lifecycleSafe ? "El piloto puede avanzar o permanecer pausado." : "El piloto ya fue completado."));

        ControlledPilotExternalEffectGuard.Decision voice =
                externalEffects.evaluateCurrentTenant(ControlledPilotExternalEffectGuard.Effect.VOICE);
        ControlledPilotExternalEffectGuard.Decision whatsapp =
                externalEffects.evaluateCurrentTenant(ControlledPilotExternalEffectGuard.Effect.WHATSAPP);
        ControlledPilotExternalEffectGuard.Decision payment =
                externalEffects.evaluateCurrentTenant(ControlledPilotExternalEffectGuard.Effect.PAYMENT);

        boolean guardEnrolled = voice.pilotManaged() && whatsapp.pilotManaged() && payment.pilotManaged();
        checks.add(new Check(
                "EXTERNAL_EFFECT_GUARD",
                "Voice, WhatsApp y pagos detrás del kill switch",
                guardEnrolled,
                true,
                guardEnrolled
                        ? "Las tres fronteras externas están bajo control del piloto."
                        : "El tenant todavía no está inscrito en pilot_launch_control."));

        boolean globalSwitchEnabled = externalEffects.globalExternalEffectsEnabled();
        boolean safeSwitchState = "RUNNING".equals(status) || !globalSwitchEnabled;
        checks.add(new Check(
                "GLOBAL_KILL_SWITCH",
                "Switch global en estado seguro",
                safeSwitchState,
                true,
                safeSwitchState
                        ? (globalSwitchEnabled ? "Piloto RUNNING con switch global habilitado." : "Switch global apagado; no hay tráfico real.")
                        : "Apaga el switch global antes de dejar el piloto fuera de RUNNING."));

        List<InventoryService.StockView> tracked = stocks.stream()
                .filter(InventoryService.StockView::trackingEnabled)
                .toList();
        int availableUnits = tracked.stream().mapToInt(InventoryService.StockView::available).sum();
        boolean inventoryReady = !tracked.isEmpty() && availableUnits > 0;
        checks.add(new Check(
                "INVENTORY",
                "Inventario vendible disponible",
                inventoryReady,
                true,
                inventoryReady
                        ? tracked.size() + " ítems con tracking; " + availableUnits + " unidades disponibles."
                        : "Configura stock con tracking y al menos una unidad disponible."));

        long outOfStockAlerts = alerts.stream()
                .filter(alert -> alert.type() == InventoryAlert.Type.OUT_OF_STOCK)
                .count();
        checks.add(new Check(
                "INVENTORY_ALERTS",
                "Sin quiebres de stock abiertos",
                outOfStockAlerts == 0,
                true,
                outOfStockAlerts == 0
                        ? "No hay alertas OUT_OF_STOCK abiertas."
                        : outOfStockAlerts + " alertas OUT_OF_STOCK requieren atención."));

        checks.add(new Check(
                "RECONCILIATION",
                "V6 sin anomalías pendientes",
                anomalies.isEmpty(),
                true,
                anomalies.isEmpty()
                        ? "No se detectan inconsistencias."
                        : anomalies.size() + " anomalías requieren revisión antes de abrir tráfico."));

        List<String> blockers = checks.stream()
                .filter(Check::required)
                .filter(check -> !check.passed())
                .map(Check::code)
                .toList();

        PilotMetricsService.Window today = pilotMetrics == null ? null : pilotMetrics.today();
        List<String> warnings = new ArrayList<>();
        long lowStock = alerts.stream()
                .filter(alert -> alert.type() == InventoryAlert.Type.LOW_STOCK)
                .count();
        if (lowStock > 0) warnings.add("LOW_STOCK:" + lowStock);
        if (today != null && today.failedPayments() > 0) warnings.add("FAILED_PAYMENTS_TODAY:" + today.failedPayments());
        if (today != null && today.callFailures() > 0) warnings.add("CALL_FAILURES_TODAY:" + today.callFailures());

        boolean armed = voice.allowed() && whatsapp.allowed() && payment.allowed();
        String trafficMode;
        if (!guardEnrolled) {
            trafficMode = "NOT_ENROLLED";
        } else if (armed) {
            trafficMode = "LIVE_ALLOWED";
        } else if (!globalSwitchEnabled) {
            trafficMode = "BLOCKED_GLOBAL";
        } else {
            trafficMode = "BLOCKED_TENANT";
        }

        OperationalSnapshot snapshot = new OperationalSnapshot(
                tracked.size(),
                availableUnits,
                lowStock,
                outOfStockAlerts,
                anomalies.size(),
                today == null ? 0 : today.orders(),
                today == null ? 0 : today.paymentAttempts(),
                today == null ? 0 : today.successfulPayments(),
                today == null ? 0 : today.pendingPayments(),
                today == null ? 0 : today.failedPayments(),
                today == null ? 0 : today.callFailures());

        return new View(
                blockers.isEmpty() ? "GO" : "NO_GO",
                status,
                trafficMode,
                globalSwitchEnabled,
                armed,
                List.copyOf(blockers),
                List.copyOf(warnings),
                List.copyOf(checks),
                snapshot);
    }

    public record Check(
            String code,
            String label,
            boolean passed,
            boolean required,
            String detail) {}

    public record OperationalSnapshot(
            long trackedInventoryItems,
            long availableInventoryUnits,
            long lowStockAlerts,
            long outOfStockAlerts,
            long reconciliationAnomalies,
            long ordersToday,
            long paymentAttemptsToday,
            long successfulPaymentsToday,
            long pendingPaymentsToday,
            long failedPaymentsToday,
            long callFailuresToday) {}

    public record View(
            String decision,
            String pilotStatus,
            String trafficMode,
            boolean globalExternalEffectsEnabled,
            boolean externalEffectsArmed,
            List<String> blockers,
            List<String> warnings,
            List<Check> checks,
            OperationalSnapshot snapshot) {}
}
