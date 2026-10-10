package cl.helvoca.onboarding;

import cl.helvoca.security.DistributedRateLimiter;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserves a provider attempt against the tenant's shared PostgreSQL limiter.
 * This is a REQUEST quota, not a dollar-denominated invoice or cost estimate.
 * Each attempt consumes capacity even if the provider times out or rejects it.
 */
@Service
public class BusinessImportAiBudget {
    private static final int WINDOW_SECONDS = 30 * 24 * 60 * 60;
    private final DistributedRateLimiter limiter;
    private final TenantProvider tenant;

    @Value("${app.onboarding.import-ai.max-attempts-per-30-days:0}")
    private int maxAttempts;

    public BusinessImportAiBudget(DistributedRateLimiter limiter, TenantProvider tenant) {
        this.limiter = limiter;
        this.tenant = tenant;
    }

    public boolean reserve() {
        if (maxAttempts <= 0) return false;
        UUID tenantId = tenant.requireBusinessId();
        return limiter.consume("business-import-ai:" + tenantId,
                maxAttempts, WINDOW_SECONDS, Instant.now()).allowed();
    }
}
