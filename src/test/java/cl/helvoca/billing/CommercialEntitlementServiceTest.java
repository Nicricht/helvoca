package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialEntitlementServiceTest {

    @Test
    void missingSubscriptionFailsClosedBeforeReadingUsage() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID businessId = UUID.randomUUID();
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.empty());

        CommercialEntitlementService service = new CommercialEntitlementService(subscriptions, catalog, jdbc);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.snapshot(businessId));
        assertTrue(error.getMessage().toLowerCase().contains("subscription"));
        verifyNoInteractions(catalog, jdbc);
    }

    @Test
    void usageComesFromV41AndSoftOverageDoesNotBlockActiveSubscription() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.now();
        BusinessSubscription subscription = subscription(
                businessId, "BASIC", SubscriptionStatus.ACTIVE,
                now.minus(5, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS), null);
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        when(catalog.requireByCode("BASIC")).thenReturn(basicPlan());
        when(jdbc.queryForObject(contains("FROM usage_meter_event"), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("6060.000000"));

        CommercialEntitlementService service = new CommercialEntitlementService(subscriptions, catalog, jdbc);
        var snapshot = service.snapshot(businessId);

        assertEquals("BASIC", snapshot.planCode());
        assertEquals("EMPRENDE", snapshot.publicPlanCode());
        assertEquals("Emprende", snapshot.planName());
        assertEquals("ACTIVE", snapshot.status());
        assertTrue(snapshot.serviceAllowed());

        var voice = snapshot.requireEntitlement("VOICE_SECONDS");
        assertEquals(new BigDecimal("6000"), voice.limit());
        assertEquals(new BigDecimal("6060.000000"), voice.used());
        assertEquals(BigDecimal.ZERO, voice.remaining());
        assertEquals(new BigDecimal("60.000000"), voice.overage());
        assertFalse(voice.hardExceeded());
        assertEquals(149, voice.overagePriceClp());

        assertEquals(1, service.capacity(businessId, "CONCURRENT_CALLS"));
        verify(jdbc, times(2)).queryForObject(contains("FROM usage_meter_event"), any(MapSqlParameterSource.class), eq(BigDecimal.class));
    }

    @Test
    void missingRequiredCapacityFailsClosed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.now();
        BusinessSubscription subscription = subscription(
                businessId, "BASIC", SubscriptionStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), null);
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        var voiceOnly = new CommercialPlanCatalogService.Plan(
                "BASIC", "EMPRENDE", "Emprende", 24_990, "CLP",
                false, false, true, 1,
                List.of(new CommercialPlanCatalogService.EntitlementRule(
                        "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", new BigDecimal("6000"),
                        "SECONDS", false, new BigDecimal("60"), 149)));
        when(catalog.requireByCode("BASIC")).thenReturn(voiceOnly);
        when(jdbc.queryForObject(contains("FROM usage_meter_event"), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(BigDecimal.ZERO);

        CommercialEntitlementService service = new CommercialEntitlementService(subscriptions, catalog, jdbc);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.capacity(businessId, "CONCURRENT_CALLS"));
        assertTrue(error.getMessage().contains("CONCURRENT_CALLS"));
    }

    @Test
    void expiredTrialIsNotServiceAllowed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.now();
        BusinessSubscription subscription = subscription(
                businessId, "BASIC", SubscriptionStatus.TRIALING,
                now.minus(20, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS), null);
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(subscription));
        when(catalog.requireByCode("BASIC")).thenReturn(basicPlan());
        when(jdbc.queryForObject(contains("FROM usage_meter_event"), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(BigDecimal.ZERO);

        CommercialEntitlementService service = new CommercialEntitlementService(subscriptions, catalog, jdbc);

        assertFalse(service.snapshot(businessId).serviceAllowed());
    }

    private static CommercialPlanCatalogService.Plan basicPlan() {
        return new CommercialPlanCatalogService.Plan(
                "BASIC", "EMPRENDE", "Emprende", 24_990, "CLP",
                false, false, true, 1,
                List.of(
                        new CommercialPlanCatalogService.EntitlementRule(
                                "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", new BigDecimal("6000"),
                                "SECONDS", false, new BigDecimal("60"), 149),
                        new CommercialPlanCatalogService.EntitlementRule(
                                "CONCURRENT_CALLS", "CAPACITY", null, BigDecimal.ONE,
                                "COUNT", true, null, null)));
    }

    private static BusinessSubscription subscription(UUID businessId,
                                                     String plan,
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
