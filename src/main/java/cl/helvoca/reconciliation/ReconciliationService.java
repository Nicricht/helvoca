package cl.helvoca.reconciliation;

import cl.helvoca.inventory.InventoryService;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {
    private final ReconciliationRepository repository;
    private final InventoryService inventory;
    private final TenantProvider tenant;
    private final Clock clock;

    @Autowired
    public ReconciliationService(
            ReconciliationRepository repository,
            InventoryService inventory,
            TenantProvider tenant) {
        this(repository, inventory, tenant, Clock.systemUTC());
    }

    ReconciliationService(
            ReconciliationRepository repository,
            InventoryService inventory,
            TenantProvider tenant,
            Clock clock) {
        this.repository = repository;
        this.inventory = inventory;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationAnomaly> detect() {
        return repository.detect(tenant.requireBusinessId(), Instant.now(clock));
    }

    @Transactional
    public RepairResult repair(ReconciliationAnomaly.Type type, UUID subjectId, Boolean dryRun) {
        if (type == null || subjectId == null) {
            throw new IllegalArgumentException("type and subjectId are required");
        }
        UUID businessId = tenant.requireBusinessId();
        ReconciliationAnomaly anomaly = repository.detect(businessId, Instant.now(clock)).stream()
                .filter(value -> value.type() == type && subjectId.equals(value.subjectId()))
                .findFirst()
                .orElse(null);

        if (anomaly == null) {
            return new RepairResult(RepairStatus.NO_LONGER_PRESENT, null, false,
                    "La anomalía ya no está presente.");
        }
        if (!anomaly.safeRepairAvailable()) {
            return new RepairResult(RepairStatus.RECOMMENDATION_ONLY, null, false,
                    anomaly.suggestedAction());
        }

        boolean safeDryRun = dryRun == null || dryRun;
        if (safeDryRun) {
            return new RepairResult(RepairStatus.DRY_RUN, null, false,
                    "La reparación es segura y está disponible; no se aplicó ningún cambio.");
        }

        String idempotencyKey = "reconciliation:" + anomaly.type().name()
                + ":" + anomaly.subjectId();
        ReconciliationRepository.ClaimResult claim =
                repository.claimRepair(businessId, anomaly, idempotencyKey);

        if (claim.state() == ReconciliationRepository.ClaimState.ALREADY_COMPLETED) {
            return new RepairResult(
                    RepairStatus.ALREADY_APPLIED, claim.actionId(), false,
                    "La reparación ya había sido aplicada idempotentemente.");
        }
        if (claim.state() == ReconciliationRepository.ClaimState.IN_PROGRESS) {
            return new RepairResult(
                    RepairStatus.IN_PROGRESS, claim.actionId(), false,
                    "Ya existe una reparación en curso para esta anomalía.");
        }

        try {
            applySafeRepair(businessId, anomaly);
            repository.complete(businessId, claim.actionId());
            return new RepairResult(
                    RepairStatus.APPLIED, claim.actionId(), true,
                    "Reparación segura aplicada.");
        } catch (RuntimeException error) {
            repository.fail(businessId, claim.actionId(), error);
            throw error;
        }
    }

    private void applySafeRepair(UUID businessId, ReconciliationAnomaly anomaly) {
        if (anomaly.operationId() == null) {
            throw new IllegalStateException("Safe reconciliation requires an operation id");
        }
        switch (anomaly.type()) {
            case PAYMENT_SUCCEEDED_INVENTORY_RESERVED ->
                    inventory.consumeOrder(
                            businessId,
                            anomaly.operationId(),
                            "V6 reconciliation: verified payment already succeeded");
            case PAYMENT_TERMINAL_INVENTORY_RESERVED ->
                    inventory.releaseOrder(
                            businessId,
                            anomaly.operationId(),
                            "V6 reconciliation: terminal payment has no active/succeeded replacement");
            case ORPHAN_RESERVATION ->
                    inventory.releaseOrder(
                            businessId,
                            anomaly.operationId(),
                            "V6 reconciliation: orphan order reservation");
            default -> throw new IllegalStateException(
                    "Anomaly is not allowlisted for automatic repair: " + anomaly.type());
        }
    }

    public enum RepairStatus {
        DRY_RUN,
        APPLIED,
        ALREADY_APPLIED,
        IN_PROGRESS,
        RECOMMENDATION_ONLY,
        NO_LONGER_PRESENT
    }

    public record RepairResult(
            RepairStatus status,
            UUID actionId,
            boolean changed,
            String message) {}
}
