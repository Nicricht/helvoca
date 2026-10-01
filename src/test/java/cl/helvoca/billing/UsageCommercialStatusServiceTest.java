package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsageCommercialStatusServiceTest {

    @Test
    void classifiesSeventyPercentAsNoticeAndKeepsOverageAtZero() {
        UUID businessId = UUID.randomUUID();
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(subscriptions.view(businessId)).thenReturn(subscription(
                businessId, new BigDecimal("4200"), new BigDecimal("6000")));

        var status = new UsageCommercialStatusService(subscriptions, tenantProvider).currentForTenant();

        assertEquals("NOTICE", status.alertLevel());
        assertEquals(new BigDecimal("70.0"), status.usagePercent());
        assertEquals(100, status.includedMinutes());
        assertEquals(70, status.usedMinutes());
        assertEquals(0, status.overageMinutes());
        assertEquals(0, status.estimatedOverageChargeClp());
        assertEquals(149, status.overagePricePerMinuteClp());
        assertEquals(1000, status.safetyLimitMinutes());
        assertFalse(status.safetyExceeded());
    }

    @Test
    void classifiesNinetyAndOneHundredPercentBoundaries() {
        assertEquals("WARNING", UsageCommercialStatusService.alertLevel(
                new BigDecimal("5400"), new BigDecimal("6000")));
        assertEquals("LIMIT", UsageCommercialStatusService.alertLevel(
                new BigDecimal("6000"), new BigDecimal("6000")));
        assertEquals("OVERAGE", UsageCommercialStatusService.alertLevel(
                new BigDecimal("6001"), new BigDecimal("6000")));
    }

    @Test
    void calculatesCeiledPricedOverageWithoutCallingItCollectedRevenue() {
        UUID businessId = UUID.randomUUID();
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(subscriptions.view(businessId)).thenReturn(subscription(
                businessId, new BigDecimal("6300"), new BigDecimal("6000")));

        var status = new UsageCommercialStatusService(subscriptions, tenantProvider).currentForTenant();

        assertEquals("OVERAGE", status.alertLevel());
        assertEquals(105, status.usedMinutes());
        assertEquals(5, status.overageMinutes());
        assertEquals(745, status.estimatedOverageChargeClp());
        assertFalse(status.safetyExceeded());
    }

    private static BusinessSubscriptionService.SubscriptionView subscription(UUID businessId,
                                                                             BigDecimal used,
                                                                             BigDecimal included) {
        BigDecimal overage = used.subtract(included).max(BigDecimal.ZERO);
        BigDecimal remaining = included.subtract(used).max(BigDecimal.ZERO);
        BigDecimal safetyLimit = included.multiply(BigDecimal.TEN);
        BigDecimal safetyRemaining = safetyLimit.subtract(used).max(BigDecimal.ZERO);
        boolean safetyExceeded = used.compareTo(safetyLimit) > 0;
        Instant now = Instant.now();

        return new BusinessSubscriptionService.SubscriptionView(
                businessId,
                "BASIC",
                "EMPRENDE",
                "Emprende",
                "ACTIVE",
                !safetyExceeded,
                1,
                included.divide(BigDecimal.valueOf(60)).intValue(),
                used.divide(BigDecimal.valueOf(60), 0, java.math.RoundingMode.CEILING).longValue(),
                overage.divide(BigDecimal.valueOf(60), 0, java.math.RoundingMode.CEILING).longValue(),
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                null,
                true,
                List.of(
                        new CommercialEntitlementService.EntitlementUsage(
                                "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", included, "SECONDS", false,
                                used, remaining, overage, false, new BigDecimal("60"), 149),
                        new CommercialEntitlementService.EntitlementUsage(
                                "VOICE_SAFETY_SECONDS", "USAGE", "VOICE_SECONDS", safetyLimit, "SECONDS", true,
                                used, safetyRemaining, used.subtract(safetyLimit).max(BigDecimal.ZERO),
                                safetyExceeded, null, null)
                ),
                false);
    }
}
