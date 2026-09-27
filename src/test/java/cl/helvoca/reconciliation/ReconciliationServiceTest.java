package cl.helvoca.reconciliation;

import cl.helvoca.inventory.InventoryService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
    @Mock ReconciliationRepository repository;
    @Mock InventoryService inventory;
    @Mock TenantProvider tenant;

    UUID businessId;
    Instant now;
    ReconciliationService service;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        now = Instant.parse("2026-09-27T17:00:00Z");
        when(tenant.requireBusinessId()).thenReturn(businessId);
        service = new ReconciliationService(
                repository, inventory, tenant, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void detectIsTenantScopedAndUsesStableClock() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.JOB_STUCK, false);
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));

        assertEquals(List.of(anomaly), service.detect());
        verify(repository).detect(businessId, now);
    }

    @Test
    void dryRunIsDefaultAndNeverMutatesSafeAnomaly() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED, true);
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));

        ReconciliationService.RepairResult result =
                service.repair(anomaly.type(), anomaly.subjectId(), null);

        assertEquals(ReconciliationService.RepairStatus.DRY_RUN, result.status());
        assertFalse(result.changed());
        verify(repository, never()).claimRepair(any(), any(), anyString());
        verifyNoInteractions(inventory);
    }

    @Test
    void unsafeAnomalyIsRecommendationOnlyEvenWhenApplyRequested() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.OUTBOUND_STUCK_PREPARED, false);
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));

        ReconciliationService.RepairResult result =
                service.repair(anomaly.type(), anomaly.subjectId(), false);

        assertEquals(ReconciliationService.RepairStatus.RECOMMENDATION_ONLY, result.status());
        assertFalse(result.changed());
        verifyNoInteractions(inventory);
        verify(repository, never()).claimRepair(any(), any(), anyString());
    }

    @Test
    void succeededPaymentConsumesOrderReservationAndCompletesAudit() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED, true);
        UUID actionId = UUID.randomUUID();
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));
        when(repository.claimRepair(eq(businessId), eq(anomaly), anyString()))
                .thenReturn(new ReconciliationRepository.ClaimResult(
                        actionId, ReconciliationRepository.ClaimState.CLAIMED));

        ReconciliationService.RepairResult result =
                service.repair(anomaly.type(), anomaly.subjectId(), false);

        assertEquals(ReconciliationService.RepairStatus.APPLIED, result.status());
        assertTrue(result.changed());
        assertEquals(actionId, result.actionId());
        verify(inventory).consumeOrder(
                eq(businessId), eq(anomaly.operationId()), contains("V6 reconciliation"));
        verify(repository).complete(businessId, actionId);
    }

    @Test
    void terminalPaymentAndOrphanReservationUseReleasePath() {
        for (ReconciliationAnomaly.Type type : List.of(
                ReconciliationAnomaly.Type.PAYMENT_TERMINAL_INVENTORY_RESERVED,
                ReconciliationAnomaly.Type.ORPHAN_RESERVATION)) {
            reset(repository, inventory);
            when(tenant.requireBusinessId()).thenReturn(businessId);
            ReconciliationAnomaly anomaly = anomaly(type, true);
            UUID actionId = UUID.randomUUID();
            when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));
            when(repository.claimRepair(eq(businessId), eq(anomaly), anyString()))
                    .thenReturn(new ReconciliationRepository.ClaimResult(
                            actionId, ReconciliationRepository.ClaimState.CLAIMED));

            ReconciliationService.RepairResult result =
                    service.repair(type, anomaly.subjectId(), false);

            assertEquals(ReconciliationService.RepairStatus.APPLIED, result.status());
            verify(inventory).releaseOrder(
                    eq(businessId), eq(anomaly.operationId()), contains("V6 reconciliation"));
            verify(repository).complete(businessId, actionId);
        }
    }

    @Test
    void completedAndInProgressClaimsDoNotRepeatMutation() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED, true);
        UUID actionId = UUID.randomUUID();
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));
        when(repository.claimRepair(eq(businessId), eq(anomaly), anyString()))
                .thenReturn(
                        new ReconciliationRepository.ClaimResult(
                                actionId, ReconciliationRepository.ClaimState.ALREADY_COMPLETED),
                        new ReconciliationRepository.ClaimResult(
                                actionId, ReconciliationRepository.ClaimState.IN_PROGRESS));

        assertEquals(ReconciliationService.RepairStatus.ALREADY_APPLIED,
                service.repair(anomaly.type(), anomaly.subjectId(), false).status());
        assertEquals(ReconciliationService.RepairStatus.IN_PROGRESS,
                service.repair(anomaly.type(), anomaly.subjectId(), false).status());

        verifyNoInteractions(inventory);
        verify(repository, never()).complete(any(), any());
    }

    @Test
    void absentAnomalyIsReportedAsAlreadyResolved() {
        when(repository.detect(businessId, now)).thenReturn(List.of());

        ReconciliationService.RepairResult result = service.repair(
                ReconciliationAnomaly.Type.ORPHAN_RESERVATION, UUID.randomUUID(), false);

        assertEquals(ReconciliationService.RepairStatus.NO_LONGER_PRESENT, result.status());
        assertFalse(result.changed());
        verifyNoInteractions(inventory);
    }

    @Test
    void repairFailureIsAuditedAndRethrown() {
        ReconciliationAnomaly anomaly = anomaly(
                ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED, true);
        UUID actionId = UUID.randomUUID();
        when(repository.detect(businessId, now)).thenReturn(List.of(anomaly));
        when(repository.claimRepair(eq(businessId), eq(anomaly), anyString()))
                .thenReturn(new ReconciliationRepository.ClaimResult(
                        actionId, ReconciliationRepository.ClaimState.CLAIMED));
        doThrow(new IllegalStateException("inventory conflict"))
                .when(inventory).consumeOrder(any(), any(), anyString());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.repair(anomaly.type(), anomaly.subjectId(), false));

        assertEquals("inventory conflict", error.getMessage());
        verify(repository).fail(businessId, actionId, error);
        verify(repository, never()).complete(any(), any());
    }

    @Test
    void invalidRepairInputFailsBeforeRepositoryLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.repair(null, UUID.randomUUID(), false));
        assertThrows(IllegalArgumentException.class,
                () -> service.repair(ReconciliationAnomaly.Type.JOB_STUCK, null, false));
        verify(repository, never()).detect(any(), any());
    }

    private ReconciliationAnomaly anomaly(
            ReconciliationAnomaly.Type type, boolean safe) {
        return new ReconciliationAnomaly(
                type,
                ReconciliationAnomaly.Severity.HIGH,
                "TEST",
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                safe,
                "suggested",
                Map.of("evidence", "value"));
    }
}
