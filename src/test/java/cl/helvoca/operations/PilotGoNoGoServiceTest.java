package cl.helvoca.operations;

import cl.helvoca.inventory.InventoryAlert;
import cl.helvoca.inventory.InventoryAlertService;
import cl.helvoca.inventory.InventoryService;
import cl.helvoca.onboarding.PilotActivationChecklistService;
import cl.helvoca.reconciliation.ReconciliationAnomaly;
import cl.helvoca.reconciliation.ReconciliationService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotGoNoGoServiceTest {
    @Mock PilotReadinessService readiness;
    @Mock PilotActivationChecklistService activation;
    @Mock PilotLaunchControlService control;
    @Mock ControlledPilotExternalEffectGuard externalEffects;
    @Mock InventoryService inventory;
    @Mock InventoryAlertService inventoryAlerts;
    @Mock ReconciliationService reconciliation;
    @Mock PilotMetricsService metrics;
    @Mock TenantProvider tenantProvider;

    private PilotGoNoGoService service;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        service = new PilotGoNoGoService(
                readiness,
                activation,
                control,
                externalEffects,
                inventory,
                inventoryAlerts,
                reconciliation,
                metrics,
                tenantProvider);
    }

    @Test
    void returnsGoWhileRealTrafficRemainsBlockedByDefault() {
        stubGreenInputs("READY", false, false);

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("GO", result.decision());
        assertEquals("READY", result.pilotStatus());
        assertEquals("BLOCKED_GLOBAL", result.trafficMode());
        assertFalse(result.globalExternalEffectsEnabled());
        assertFalse(result.externalEffectsArmed());
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, result.snapshot().trackedInventoryItems());
        assertEquals(5, result.snapshot().availableInventoryUnits());
        assertEquals(0, result.snapshot().reconciliationAnomalies());
    }

    @Test
    void reconciliationAnomalyAndMissingInventoryForceNoGo() {
        stubGreenInputs("READY", false, false);
        when(inventory.list()).thenReturn(List.of());
        when(reconciliation.detect()).thenReturn(List.of(new ReconciliationAnomaly(
                ReconciliationAnomaly.Type.JOURNEY_STATE_MISMATCH,
                ReconciliationAnomaly.Severity.HIGH,
                "JOURNEY",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                false,
                "Review journey state",
                Map.of())));

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("NO_GO", result.decision());
        assertTrue(result.blockers().contains("INVENTORY"));
        assertTrue(result.blockers().contains("RECONCILIATION"));
        assertEquals(1, result.snapshot().reconciliationAnomalies());
    }

    @Test
    void runningPilotWithExplicitGlobalOptInReportsLiveAllowed() {
        stubGreenInputs("RUNNING", true, true);

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("GO", result.decision());
        assertEquals("LIVE_ALLOWED", result.trafficMode());
        assertTrue(result.globalExternalEffectsEnabled());
        assertTrue(result.externalEffectsArmed());
    }

    private void stubGreenInputs(String status, boolean globalEnabled, boolean effectsAllowed) {
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true,
                1,
                1,
                List.of(new PilotReadinessService.Check(
                        "VOICE", "Llamadas con IA", true, "Voz lista.")),
                List.of(),
                "gemini",
                "mercadopago",
                "SANDBOX"));

        when(activation.current()).thenReturn(new PilotActivationChecklistService.View(
                true,
                1,
                1,
                100,
                List.of(),
                List.of(new PilotActivationChecklistService.Step(
                        "PILOT_SCOPE_APPROVED",
                        "Alcance aprobado",
                        true,
                        false,
                        true,
                        "/settings.html"))));

        when(control.current()).thenReturn(new PilotLaunchControlService.View(
                status,
                "GO",
                true,
                true,
                List.of(),
                "Carla",
                "+56911111111",
                "Validar primera jornada",
                Instant.parse("2026-10-10T03:00:00Z"),
                "RUNNING".equals(status) ? Instant.now() : null,
                null,
                "READY".equals(status),
                "RUNNING".equals(status),
                "PAUSED".equals(status),
                "RUNNING".equals(status) || "PAUSED".equals(status)));

        when(inventory.list()).thenReturn(List.of(new InventoryService.StockView(
                UUID.randomUUID(),
                "Producto piloto",
                "PILOT-1",
                true,
                8,
                3,
                5,
                2,
                false)));
        when(inventoryAlerts.listOpen()).thenReturn(List.of());
        when(reconciliation.detect()).thenReturn(List.of());
        when(metrics.metrics()).thenReturn(metrics());

        when(externalEffects.globalExternalEffectsEnabled()).thenReturn(globalEnabled);
        for (ControlledPilotExternalEffectGuard.Effect effect
                : ControlledPilotExternalEffectGuard.Effect.values()) {
            when(externalEffects.evaluate(businessId, effect)).thenReturn(
                    new ControlledPilotExternalEffectGuard.Decision(
                            effectsAllowed,
                            true,
                            effectsAllowed ? "ALLOWED" : "GLOBAL_KILL_SWITCH_ACTIVE",
                            status,
                            effect));
        }
    }

    private static PilotMetricsService.Metrics metrics() {
        Instant now = Instant.parse("2026-09-27T18:00:00Z");
        PilotMetricsService.Window window = new PilotMetricsService.Window(
                "TODAY",
                "Hoy",
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                0,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                0,
                0,
                0,
                Map.of("CLP", new BigDecimal("12990")),
                new BigDecimal("50.0"),
                new BigDecimal("100.0"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        return new PilotMetricsService.Metrics(
                "Negocio piloto",
                "America/Santiago",
                "2026-09-27T14:00:00-04:00",
                window,
                window);
    }
}
