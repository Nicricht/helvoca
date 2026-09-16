package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessSubscriptionServiceTest {

    @Test
    void missingSubscriptionFailsClosedInsteadOfGrantingLegacyPro() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CommercialEntitlementService entitlements = mock(CommercialEntitlementService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        when(entitlements.snapshot(businessId))
                .thenThrow(new IllegalStateException("Business subscription is not initialized"));

        var service = new BusinessSubscriptionService(repository, entitlements, tenantProvider, catalog);

        assertThrows(IllegalStateException.class, () -> service.view(businessId));
        verify(entitlements).snapshot(businessId);
        verifyNoInteractions(catalog);
    }

    @Test
    void compatibilityVoiceFieldsComeFromGenericEntitlementSnapshot() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CommercialEntitlementService entitlements = mock(CommercialEntitlementService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now().plusSeconds(86400);
        when(entitlements.snapshot(businessId)).thenReturn(snapshot(
                businessId, start, end, true, new BigDecimal("6060.000000")));

        var service = new BusinessSubscriptionService(repository, entitlements, tenantProvider, catalog);
        var view = service.view(businessId);

        assertEquals("BASIC", view.plan());
        assertEquals("EMPRENDE", view.publicPlanCode());
        assertEquals("Emprende", view.planName());
        assertEquals("ACTIVE", view.status());
        assertTrue(view.serviceAllowed());
        assertEquals(1, view.maxConcurrentCalls());
        assertEquals(100, view.includedMinutes());
        assertEquals(101, view.usedMinutes());
        assertEquals(1, view.overageMinutes());
        assertEquals(2, view.entitlements().size());
        assertFalse(view.legacyFallback());
        assertEquals(start, view.currentPeriodStart());
        assertEquals(end, view.currentPeriodEnd());
    }

    @Test
    void currentTenantIdentityComesFromTenantProvider() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CommercialEntitlementService entitlements = mock(CommercialEntitlementService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(entitlements.snapshot(businessId)).thenReturn(snapshot(
                businessId, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), true, BigDecimal.ZERO));

        var service = new BusinessSubscriptionService(repository, entitlements, tenantProvider, catalog);

        assertEquals(businessId, service.currentForTenant().businessId());
        verify(tenantProvider).requireBusinessId();
    }

    @Test
    void newBusinessStillGetsBasicTrialUsingTechnicalStringCode() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        UUID businessId = UUID.randomUUID();
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(BusinessSubscription.class))).thenAnswer(i -> i.getArgument(0));

        var service = new BusinessSubscriptionService(
                repository, mock(CommercialEntitlementService.class), mock(TenantProvider.class),
                mock(CommercialPlanCatalogService.class));
        BusinessSubscription created = service.startBasicTrial(businessId);

        assertEquals(businessId, created.getBusinessId());
        assertEquals("BASIC", created.getPlanCode());
        assertEquals(SubscriptionStatus.TRIALING, created.getStatus());
        assertTrue(created.getCurrentPeriodEnd().isAfter(created.getCurrentPeriodStart()));
    }

    private static CommercialEntitlementService.SubscriptionEntitlements snapshot(
            UUID businessId, Instant start, Instant end, boolean allowed, BigDecimal usedVoiceSeconds) {
        BigDecimal voiceLimit = new BigDecimal("6000");
        BigDecimal overage = usedVoiceSeconds.subtract(voiceLimit).max(BigDecimal.ZERO);
        BigDecimal remaining = voiceLimit.subtract(usedVoiceSeconds).max(BigDecimal.ZERO);
        return new CommercialEntitlementService.SubscriptionEntitlements(
                businessId, "BASIC", "EMPRENDE", "Emprende", "ACTIVE", allowed,
                start, end, null, false,
                List.of(
                        new CommercialEntitlementService.EntitlementUsage(
                                "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", voiceLimit, "SECONDS", false,
                                usedVoiceSeconds, remaining, overage, false, new BigDecimal("60"), 149),
                        new CommercialEntitlementService.EntitlementUsage(
                                "CONCURRENT_CALLS", "CAPACITY", null, BigDecimal.ONE, "COUNT", true,
                                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, false, null, null)));
    }
}
