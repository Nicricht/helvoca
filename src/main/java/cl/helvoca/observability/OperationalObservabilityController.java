package cl.helvoca.observability;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/observability")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class OperationalObservabilityController {
    private final OperationalSnapshotService snapshots;
    private final TenantProvider tenants;

    public OperationalObservabilityController(OperationalSnapshotService snapshots,
                                              TenantProvider tenants) {
        this.snapshots = snapshots;
        this.tenants = tenants;
    }

    @GetMapping("/summary")
    public ResponseEntity<OperationalSnapshotService.Snapshot> summary() {
        UUID businessId = tenants.requireBusinessId();
        return ResponseEntity.ok(snapshots.snapshot(businessId));
    }
}
