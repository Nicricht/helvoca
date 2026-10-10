package cl.helvoca.onboarding;

import cl.helvoca.security.DistributedRateLimiter;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessImportAiBudgetTest {
    private static final int WINDOW = 2592000;
    private static final DistributedRateLimiter.Result ALLOW =
            new DistributedRateLimiter.Result(true, 1, 1, 10);
    private static final DistributedRateLimiter.Result DENY =
            new DistributedRateLimiter.Result(false, 4, 0, 10);

    private static void configure(BusinessImportAiBudget guard, int attempts, int centsPerCall,
                                  int tenantCents, int globalCents) {
        ReflectionTestUtils.setField(guard, "maxAttempts", attempts);
        ReflectionTestUtils.setField(guard, "reservedCentsPerAttempt", centsPerCall);
        ReflectionTestUtils.setField(guard, "maxTenantReservedCents", tenantCents);
        ReflectionTestUtils.setField(guard, "maxGlobalReservedCents", globalCents);
    }

    @Test
    void defaultZeroProtectsProviderWithoutTouchingTenantOrPostgres() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        assertFalse(new BusinessImportAiBudget(limiter, tenant).reserve());
        verifyNoInteractions(limiter, tenant);
    }

    @Test
    void eachMissingOrInvalidCommercialBudgetFailsClosed() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        for (int[] limits : new int[][] {
                {-1, 20, 100, 100},
                {2, 0, 100, 100},
                {2, -10, 100, 100},
                {2, 20, 19, 100},
                {2, 20, -1, 100},
                {2, 20, 100, 19},
                {2, 20, 100, -1}
        }) {
            configure(budget, limits[0], limits[1], limits[2], limits[3]);
            assertFalse(budget.reserve());
        }
        verifyNoInteractions(limiter, tenant);
    }

    @Test
    void reservesOneAtomicTenantAttemptAndTwoPricedBucketsPerAllowedRequest() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID first = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(first);
        when(limiter.consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class))).thenReturn(ALLOW);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        configure(budget, 7, 25, 125, 250);

        assertTrue(budget.reserve());
        verify(limiter).consume(eq("business-import-ai:" + first), eq(7), eq(WINDOW), any(Instant.class));
        verify(limiter).consume(eq("business-import-ai-reserved-cost:tenant:" + first),
                eq(5), eq(WINDOW), any(Instant.class));
        verify(limiter).consume(eq("business-import-ai-reserved-cost:global"),
                eq(10), eq(WINDOW), any(Instant.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void tenantAttemptQuotaRejectsBeforeAnyCostReservation() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID business = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(business);
        when(limiter.consume(eq("business-import-ai:" + business), eq(2), eq(WINDOW),
                any(Instant.class))).thenReturn(DENY);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        configure(budget, 2, 25, 50, 100);

        assertFalse(budget.reserve());
        verify(limiter).consume(eq("business-import-ai:" + business), eq(2), eq(WINDOW), any(Instant.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void exhaustedTenantReservedCentsNeverTouchGlobalCostBucket() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID business = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(business);
        when(limiter.consume(eq("business-import-ai:" + business), anyInt(), eq(WINDOW),
                any(Instant.class))).thenReturn(ALLOW);
        when(limiter.consume(eq("business-import-ai-reserved-cost:tenant:" + business),
                eq(2), eq(WINDOW), any(Instant.class))).thenReturn(DENY);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        configure(budget, 4, 25, 50, 100);

        assertFalse(budget.reserve());
        verify(limiter, never()).consume(eq("business-import-ai-reserved-cost:global"),
                anyInt(), anyInt(), any(Instant.class));
    }

    @Test
    void exhaustedGlobalBudgetRejectsEvenWhenTenantHasRoom() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(limiter.consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class)))
                .thenReturn(ALLOW);
        when(limiter.consume(eq("business-import-ai-reserved-cost:global"), eq(3),
                eq(WINDOW), any(Instant.class))).thenReturn(DENY);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        configure(budget, 20, 25, 50, 75);

        assertFalse(budget.reserve());
    }

    @Test
    void databaseFailureCannotAuthorizePaidProviderCall() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());
        when(limiter.consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class)))
                .thenThrow(new IllegalStateException("quota database offline"));
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant);
        configure(budget, 1, 25, 50, 75);

        assertThrows(IllegalStateException.class, budget::reserve);
    }

    @Test
    void productionConstructorDeniesPaidImportWhenPlanHasNoRight() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiPlanQuota quota = mock(BusinessImportAiPlanQuota.class);
        when(quota.current()).thenReturn(new BusinessImportAiPlanQuota.Snapshot("NOT_INCLUDED", "BASIC",
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, null, null, true));
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant, quota);
        configure(budget, 2, 25, 50, 100);
        assertFalse(budget.reserve());
        verify(quota).current();
        verify(quota, never()).reserve(any(UUID.class));
        verifyNoInteractions(limiter, tenant);
    }

    @Test
    void concurrentQuotaExhaustionAfterBudgetReservationStillPreventsProviderCall() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiPlanQuota quota = mock(BusinessImportAiPlanQuota.class);
        UUID business = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(business);
        when(quota.current()).thenReturn(new BusinessImportAiPlanQuota.Snapshot("AVAILABLE", "BASIC",
                java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ONE, null, null, true));
        when(quota.reserve(any(UUID.class))).thenReturn(false);
        when(limiter.consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class))).thenReturn(ALLOW);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant, quota);
        configure(budget, 2, 25, 50, 100);

        assertFalse(budget.reserve());
        verify(quota).current();
        verify(quota).reserve(any(UUID.class));
        verify(limiter, times(3)).consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class));
    }

    @Test
    void approvedPlanStillRequiresAllExistingFinancialCircuitBreakers() {
        DistributedRateLimiter limiter = mock(DistributedRateLimiter.class);
        TenantProvider tenant = mock(TenantProvider.class);
        BusinessImportAiPlanQuota quota = mock(BusinessImportAiPlanQuota.class);
        UUID business = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(business);
        when(quota.current()).thenReturn(new BusinessImportAiPlanQuota.Snapshot("AVAILABLE", "BASIC",
                java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ONE, null, null, true));
        when(quota.reserve(any(UUID.class))).thenReturn(true);
        when(limiter.consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class))).thenReturn(ALLOW);
        BusinessImportAiBudget budget = new BusinessImportAiBudget(limiter, tenant, quota);
        configure(budget, 2, 25, 50, 100);
        assertTrue(budget.reserve());
        verify(quota).reserve(any(UUID.class));
        verify(limiter, times(3)).consume(anyString(), anyInt(), eq(WINDOW), any(Instant.class));
    }
}
