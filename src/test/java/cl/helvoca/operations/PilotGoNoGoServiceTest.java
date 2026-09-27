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

    @Test
    void missingConfigurationAndUnmanagedTenantFailClosedWithNullMetrics() {
        when(readiness.readiness()).thenReturn(null);
        when(activation.current()).thenReturn(null);
        when(control.current()).thenReturn(null);
        when(inventory.list()).thenReturn(List.of(new InventoryService.StockView(
                UUID.randomUUID(),
                "Sin tracking",
                "NO-TRACK",
                false,
                9,
                0,
                9,
                0,
                false)));
        when(inventoryAlerts.listOpen()).thenReturn(List.of(
                alert(InventoryAlert.Type.LOW_STOCK, 1),
                alert(InventoryAlert.Type.OUT_OF_STOCK, 0)));
        when(reconciliation.detect()).thenReturn(List.of(anomaly()));
        when(metrics.metrics()).thenReturn(null);
        when(externalEffects.globalExternalEffectsEnabled()).thenReturn(true);
        when(externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(decision(false, false, "NOT_PILOT_MANAGED", "DRAFT",
                        ControlledPilotExternalEffectGuard.Effect.VOICE));
        when(externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.WHATSAPP))
                .thenReturn(decision(false, true, "PILOT_NOT_RUNNING", "DRAFT",
                        ControlledPilotExternalEffectGuard.Effect.WHATSAPP));
        when(externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.PAYMENT))
                .thenReturn(decision(false, true, "PILOT_NOT_RUNNING", "DRAFT",
                        ControlledPilotExternalEffectGuard.Effect.PAYMENT));

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("NO_GO", result.decision());
        assertEquals("DRAFT", result.pilotStatus());
        assertEquals("NOT_ENROLLED", result.trafficMode());
        assertTrue(result.blockers().contains("PILOT_CONFIGURATION"));
        assertTrue(result.blockers().contains("EXTERNAL_EFFECT_GUARD"));
        assertTrue(result.blockers().contains("GLOBAL_KILL_SWITCH"));
        assertTrue(result.blockers().contains("INVENTORY"));
        assertTrue(result.blockers().contains("INVENTORY_ALERTS"));
        assertTrue(result.blockers().contains("RECONCILIATION"));
        assertEquals(List.of("LOW_STOCK:1"), result.warnings());
        assertEquals(0, result.snapshot().trackedInventoryItems());
        assertEquals(0, result.snapshot().availableInventoryUnits());
        assertEquals(1, result.snapshot().lowStockAlerts());
        assertEquals(1, result.snapshot().outOfStockAlerts());
        assertEquals(1, result.snapshot().reconciliationAnomalies());
        assertEquals(0, result.snapshot().ordersToday());
        assertEquals(0, result.snapshot().paymentAttemptsToday());
        assertEquals(0, result.snapshot().callFailuresToday());
    }

    @Test
    void pausedManagedPilotWithGlobalSwitchOnIsBlockedAndSurfacesOperationalWarnings() {
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                false,
                0,
                0,
                null,
                List.of("voice"),
                "gemini",
                "mercadopago",
                "SANDBOX"));
        when(activation.current()).thenReturn(new PilotActivationChecklistService.View(
                false,
                0,
                1,
                0,
                List.of("PILOT_SCOPE_APPROVAL_REQUIRED"),
                List.of(
                        new PilotActivationChecklistService.Step(
                                "OPTIONAL",
                                "Paso opcional",
                                false,
                                false,
                                false,
                                "/"),
                        new PilotActivationChecklistService.Step(
                                "PILOT_SCOPE_APPROVED",
                                "Alcance aprobado",
                                false,
                                false,
                                true,
                                "/settings.html"))));
        when(control.current()).thenReturn(new PilotLaunchControlService.View(
                "PAUSED",
                "PAUSED",
                true,
                true,
                List.of(),
                "Carla",
                "+56911111111",
                "Validar primera jornada",
                Instant.parse("2026-10-10T03:00:00Z"),
                Instant.parse("2026-09-27T18:00:00Z"),
                null,
                false,
                false,
                true,
                true));
        when(inventory.list()).thenReturn(List.of(
                new InventoryService.StockView(
                        UUID.randomUUID(), "Producto", "SKU-1", true, 8, 3, 5, 2, false),
                new InventoryService.StockView(
                        UUID.randomUUID(), "Sin tracking", "SKU-2", false, 8, 0, 8, 0, false)));
        when(inventoryAlerts.listOpen()).thenReturn(List.of(
                alert(InventoryAlert.Type.LOW_STOCK, 1),
                alert(InventoryAlert.Type.OUT_OF_STOCK, 0)));
        when(reconciliation.detect()).thenReturn(List.of());
        when(metrics.metrics()).thenReturn(metricsWithFailures());
        when(externalEffects.globalExternalEffectsEnabled()).thenReturn(true);
        for (ControlledPilotExternalEffectGuard.Effect effect
                : ControlledPilotExternalEffectGuard.Effect.values()) {
            when(externalEffects.evaluate(businessId, effect)).thenReturn(
                    decision(false, true, "PILOT_NOT_RUNNING", "PAUSED", effect));
        }

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("NO_GO", result.decision());
        assertEquals("BLOCKED_TENANT", result.trafficMode());
        assertTrue(result.blockers().contains("ACTIVATION_PILOT_SCOPE_APPROVED"));
        assertTrue(result.blockers().contains("GLOBAL_KILL_SWITCH"));
        assertTrue(result.blockers().contains("INVENTORY_ALERTS"));
        assertEquals(
                List.of("LOW_STOCK:1", "FAILED_PAYMENTS_TODAY:2", "CALL_FAILURES_TODAY:3"),
                result.warnings());
        assertEquals(5, result.snapshot().availableInventoryUnits());
        assertEquals(4, result.snapshot().paymentAttemptsToday());
        assertEquals(1, result.snapshot().successfulPaymentsToday());
        assertEquals(2, result.snapshot().failedPaymentsToday());
        assertEquals(3, result.snapshot().callFailuresToday());
    }

    @Test
    void partiallyArmedManagedPilotStillBlocksTenantTraffic() {
        stubGreenInputs("RUNNING", true, true);
        when(externalEffects.evaluate(businessId, ControlledPilotExternalEffectGuard.Effect.WHATSAPP))
                .thenReturn(decision(
                        false,
                        true,
                        "PILOT_NOT_RUNNING",
                        "RUNNING",
                        ControlledPilotExternalEffectGuard.Effect.WHATSAPP));

        PilotGoNoGoService.View result = service.evaluate();

        assertEquals("GO", result.decision());
        assertEquals("BLOCKED_TENANT", result.trafficMode());
        assertFalse(result.externalEffectsArmed());
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

    private static ControlledPilotExternalEffectGuard.Decision decision(
            boolean allowed,
            boolean managed,
            String code,
            String status,
            ControlledPilotExternalEffectGuard.Effect effect) {
        return new ControlledPilotExternalEffectGuard.Decision(
                allowed, managed, code, status, effect);
    }

    private static InventoryAlertService.AlertView alert(InventoryAlert.Type type, int available) {
        return new InventoryAlertService.AlertView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                type,
                "Producto",
                "SKU",
                available,
                2,
                false,
                null,
                Instant.parse("2026-09-27T18:00:00Z"));
    }

    private static ReconciliationAnomaly anomaly() {
        return new ReconciliationAnomaly(
                ReconciliationAnomaly.Type.JOURNEY_STATE_MISMATCH,
                ReconciliationAnomaly.Severity.HIGH,
                "JOURNEY",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-09-27T18:00:00Z"),
                false,
                "Review journey state",
                Map.of());
    }

    private static PilotMetricsService.Metrics metricsWithFailures() {
        Instant now = Instant.parse("2026-09-27T18:00:00Z");
        PilotMetricsService.Window window = new PilotMetricsService.Window(
                "TODAY",
                "Hoy",
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                5,
                2,
                0,
                3,
                4,
                1,
                1,
                2,
                0,
                0,
                3,
                Map.of("CLP", new BigDecimal("12990")),
                new BigDecimal("33.3"),
                new BigDecimal("25.0"),
                new BigDecimal("60.0"),
                BigDecimal.ZERO);
        return new PilotMetricsService.Metrics(
                "Negocio piloto",
                "America/Santiago",
                "2026-09-27T14:00:00-04:00",
                window,
                window);
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
