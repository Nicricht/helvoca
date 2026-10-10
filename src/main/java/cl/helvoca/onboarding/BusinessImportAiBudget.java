package cl.helvoca.onboarding;

import cl.helvoca.security.DistributedRateLimiter;
import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Conservative preauthorization for paid document analysis.
 *
 * These limits reserve OPERATOR-ESTIMATED cents for each provider attempt.
 * They are not a provider invoice or a claim that actual tokens have a
 * fixed price. Until pricing/usage reconciliation and a provider hard cap
 * are validated, this circuit breaker must remain disabled in production.
 *
 * A denied later check can consume earlier reservations (fail closed).
 * Buckets are fixed 30-day windows shared by all application instances.
 */
@Service
public class BusinessImportAiBudget {
    private static final int WINDOW_SECONDS = 30 * 24 * 60 * 60;
    private static final String GLOBAL_COST_BUCKET = "business-import-ai-reserved-cost:global";

    private final DistributedRateLimiter limiter;
    private final TenantProvider tenant;
    private final BusinessImportAiPlanQuota planQuota;

    @Value("${app.onboarding.import-ai.max-attempts-per-30-days:0}")
    private int maxAttempts;

    @Value("${app.onboarding.import-ai.reserved-cents-per-attempt:0}")
    private int reservedCentsPerAttempt;

    @Value("${app.onboarding.import-ai.max-tenant-reserved-cents-per-30-days:0}")
    private int maxTenantReservedCents;

    @Value("${app.onboarding.import-ai.max-global-reserved-cents-per-30-days:0}")
    private int maxGlobalReservedCents;

    @Autowired
    public BusinessImportAiBudget(DistributedRateLimiter limiter, TenantProvider tenant,
                                  BusinessImportAiPlanQuota planQuota) {
        this.limiter = limiter;
        this.tenant = tenant;
        this.planQuota = planQuota;
    }

    // Existing focused tests use this constructor without a database-backed quota.
    public BusinessImportAiBudget(DistributedRateLimiter limiter, TenantProvider tenant) {
        this(limiter, tenant, null);
    }

    public boolean reserve() {
        // All knobs must be intentionally configured. Zero is an emergency stop.
        if (maxAttempts <= 0 || reservedCentsPerAttempt <= 0
                || maxTenantReservedCents < reservedCentsPerAttempt
                || maxGlobalReservedCents < reservedCentsPerAttempt) return false;

        // Preflight BEFORE consuming shared budgets; an ineligible tenant cannot
        // drain the global pool. Atomic subscription reservation is performed
        // AFTER the budget checks to avoid charging a plan for budget denials.
        if (planQuota != null && !"AVAILABLE".equals(planQuota.current().status())) return false;

        UUID businessId = tenant.requireBusinessId();
        Instant now = Instant.now();
        if (!limiter.consume("business-import-ai:" + businessId,
                maxAttempts, WINDOW_SECONDS, now).allowed()) return false;

        // Do not use two uncoordinated in-memory counters. Each PostgreSQL
        // bucket is atomic across instances and business identities.
        if (!limiter.consume("business-import-ai-reserved-cost:tenant:" + businessId,
                maxTenantReservedCents / reservedCentsPerAttempt,
                WINDOW_SECONDS, now).allowed()) return false;

        // The global reservation is the final authority across all tenants.
        // A failed or unknown provider response never refunds a reservation.
        if (!limiter.consume(GLOBAL_COST_BUCKET,
                maxGlobalReservedCents / reservedCentsPerAttempt,
                WINDOW_SECONDS, now).allowed()) return false;
        // A concurrent loser may consume an earlier conservative budget bucket,
        // never a customer's subscription slot or a paid provider request.
        return planQuota == null || planQuota.reserve(UUID.randomUUID());
    }
}
