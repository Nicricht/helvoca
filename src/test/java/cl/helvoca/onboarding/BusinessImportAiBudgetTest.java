package cl.helvoca.onboarding;

import cl.helvoca.security.DistributedRateLimiter;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessImportAiBudgetTest {
    @Test
    void defaultsToZeroAndNeverTouchesPostgresOrTenantClaims() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        assertFalse(budget.reserve());
        verifyNoInteractions(limiter, tenant);
    }

    @Test
    void tenantBoundedQuotaIsAtomicAtExistingPostgresqlLimiterBoundary() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(first, second);
        when(limiter.consume(anyString(), eq(2), eq(2592000), any(Instant.class)))
                .thenReturn(new DistributedRateLimiter.Result(true, 1, 1, 10),
                        new DistributedRateLimiter.Result(false, 3, 0, 10));
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        ReflectionTestUtils.setField(budget, "maxAttempts", 2);

        assertTrue(budget.reserve());
        assertFalse(budget.reserve());
        verify(limiter).consume(eq("business-import-ai:" + first), eq(2), eq(2592000), any(Instant.class));
        verify(limiter).consume(eq("business-import-ai:" + second), eq(2), eq(2592000), any(Instant.class));
    }

    @Test
    void invalidNegativeLimitAlsoFailsClosed() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        ReflectionTestUtils.setField(budget, "maxAttempts", -1);
        assertFalse(budget.reserve());
        verifyNoInteractions(limiter, tenant);
    }

    @Test
    void postgresFailureMustNotApprovePaidProviderCall() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(limiter.consume(anyString(), eq(1), eq(2592000), any(Instant.class)))
                .thenThrow(new IllegalStateException("quota database offline"));
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        ReflectionTestUtils.setField(budget, "maxAttempts", 1);

        assertThrows(IllegalStateException.class, budget::reserve);
    }
}
