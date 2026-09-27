package cl.helvoca.reconciliation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class ReconciliationController {
    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping("/anomalies")
    public List<ReconciliationAnomaly> anomalies() {
        return reconciliation.detect();
    }

    @PostMapping("/repair")
    public ReconciliationService.RepairResult repair(@RequestBody RepairRequest request) {
        if (request == null) throw new IllegalArgumentException("Repair request is required");
        return reconciliation.repair(request.type(), request.subjectId(), request.dryRun());
    }

    public record RepairRequest(
            ReconciliationAnomaly.Type type,
            UUID subjectId,
            Boolean dryRun) {}
}
