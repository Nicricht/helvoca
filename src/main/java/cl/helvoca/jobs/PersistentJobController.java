package cl.helvoca.jobs;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class PersistentJobController {
    private final PersistentJobService jobs;
    private final TenantProvider tenantProvider;

    public PersistentJobController(PersistentJobService jobs, TenantProvider tenantProvider) {
        this.jobs = jobs;
        this.tenantProvider = tenantProvider;
    }

    @GetMapping
    public ResponseEntity<List<View>> recent() {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(jobs.recent(businessId).stream().map(View::from).toList());
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID jobId) {
        UUID businessId = tenantProvider.requireBusinessId();
        return jobs.cancel(businessId, jobId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public record View(UUID id,
                       UUID operationId,
                       String type,
                       String status,
                       int attemptCount,
                       int maxAttempts,
                       Instant nextAttemptAt,
                       Instant leaseExpiresAt,
                       String lastErrorCode,
                       Instant completedAt,
                       Instant createdAt,
                       Instant updatedAt) {
        static View from(PersistentJob job) {
            return new View(job.id(), job.operationId(), job.jobType().name(), job.status().name(),
                    job.attemptCount(), job.maxAttempts(), job.nextAttemptAt(), job.leaseExpiresAt(),
                    job.lastErrorCode(), job.completedAt(), job.createdAt(), job.updatedAt());
        }
    }
}
