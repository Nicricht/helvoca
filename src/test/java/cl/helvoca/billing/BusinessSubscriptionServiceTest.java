package cl.helvoca.billing;

import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.CallCommercialProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessSubscriptionServiceTest {

    @Test
    void missingSubscriptionPreservesLegacyCapacity() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(25);
        UUID businessId = UUID.randomUUID();
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(calls.sumDurationSecondsByBusinessAndPeriod(eq(businessId), any(), any(), eq("simulator")))
                .thenReturn(61L);

        var service = new BusinessSubscriptionService(repository, calls, mock(TenantProvider.class), properties);
        var view = service.view(businessId);

        assertEquals("PRO", view.plan());
        assertTrue(view.serviceAllowed());
        assertTrue(view.legacyFallback());
        assertEquals(25, view.maxConcurrentCalls());
        assertEquals(2, view.usedMinutes());
    }

    @Test
    void expiredTrialBlocksService() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription subscription = subscription(
                businessId, PlanCode.BASIC, SubscriptionStatus.TRIALING,
                Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().minus(6, ChronoUnit.DAYS), null);
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        when(calls.sumDurationSecondsByBusinessAndPeriod(eq(businessId), any(), any(), eq("simulator")))
                .thenReturn(0L);

        var service = new BusinessSubscriptionService(
                repository, calls, mock(TenantProvider.class), new CallCommercialProperties());

        assertFalse(service.view(businessId).serviceAllowed());
    }

    @Test
    void pastDueWithinGraceStillAllowsService() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.now();
        BusinessSubscription subscription = subscription(
                businessId, PlanCode.PRO, SubscriptionStatus.PAST_DUE,
                now.minus(10, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS));
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        when(calls.sumDurationSecondsByBusinessAndPeriod(eq(businessId), any(), any(), eq("simulator")))
                .thenReturn(0L);

        var service = new BusinessSubscriptionService(
                repository, calls, mock(TenantProvider.class), new CallCommercialProperties());

        assertTrue(service.view(businessId).serviceAllowed());
    }

    @Test
    void usageAboveIncludedMinutesBecomesOverageWithoutBlockingService() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.now();
        BusinessSubscription subscription = subscription(
                businessId, PlanCode.BASIC, SubscriptionStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), null);
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        when(calls.sumDurationSecondsByBusinessAndPeriod(eq(businessId), any(), any(), eq("simulator")))
                .thenReturn(101L * 60L);

        var service = new BusinessSubscriptionService(
                repository, calls, mock(TenantProvider.class), new CallCommercialProperties());
        var view = service.view(businessId);

        assertEquals(101, view.usedMinutes());
        assertEquals(1, view.overageMinutes());
        assertTrue(view.serviceAllowed());
    }

    @Test
    void newBusinessGetsBasicTrial() {
        BusinessSubscriptionRepository repository = mock(BusinessSubscriptionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        UUID businessId = UUID.randomUUID();
        when(repository.findByBusinessId(businessId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(BusinessSubscription.class))).thenAnswer(i -> i.getArgument(0));

        var service = new BusinessSubscriptionService(
                repository, calls, mock(TenantProvider.class), new CallCommercialProperties());
        BusinessSubscription created = service.startBasicTrial(businessId);

        assertEquals(businessId, created.getBusinessId());
        assertEquals(PlanCode.BASIC, created.getPlanCode());
        assertEquals(SubscriptionStatus.TRIALING, created.getStatus());
        assertTrue(created.getCurrentPeriodEnd().isAfter(created.getCurrentPeriodStart()));
    }

    private static BusinessSubscription subscription(UUID businessId,
                                                     PlanCode plan,
                                                     SubscriptionStatus status,
                                                     Instant start,
                                                     Instant end,
                                                     Instant graceUntil) {
        BusinessSubscription subscription = new BusinessSubscription();
        subscription.setBusinessId(businessId);
        subscription.setPlanCode(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(end);
        subscription.setGraceUntil(graceUntil);
        return subscription;
    }
}
