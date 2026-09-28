package cl.helvoca.billing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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

class MercadoPagoSaasSandboxInvoiceBridgeIT {

    private static final String SUBSCRIPTION_ID = "31d1df7075374596a86cecf49f3f8855";
    private static final String INVOICE_ID = "7032302146";
    private static final UUID BUSINESS_ID = UUID.fromString("89a6d662-2574-4388-ad9b-169077434cf7");

    @Test
    void approvedRealSandboxInvoiceActivatesPlanEntitlementsAndIsIdempotent() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getenv("HELVOCA_SAAS_BILLING_SANDBOX_LIVE_TEST")),
                "External Mercado Pago sandbox certification is disabled");

        String token = requiredEnv("MERCADOPAGO_TEST_ACCESS_TOKEN");
        assertTrue(token.startsWith("APP_USR"), "TEST access token must start with APP_USR");

        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken(token);
        properties.setBackUrl("https://recepvoz.cl/pricing.html");

        MercadoPagoSubscriptionGateway realGateway = new MercadoPagoSubscriptionGateway(properties);

        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);

        BusinessSubscription local = new BusinessSubscription();
        local.setBusinessId(BUSINESS_ID);
        local.setPlanCode("BASIC");
        local.setStatus(SubscriptionStatus.TRIALING);
        local.setCurrentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        local.setCurrentPeriodEnd(Instant.now().plus(13, ChronoUnit.DAYS));
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("BASIC");
        local.setExternalSubscriptionId(SUBSCRIPTION_ID);
        local.setBillingCheckoutUrl("sandbox-checkout");

        CommercialPlanCatalogService.Plan emprende = new CommercialPlanCatalogService.Plan(
                "BASIC", "EMPRENDE", "Emprende", 24_990, "CLP",
                false, false, true, 1,
                List.of(
                        new CommercialPlanCatalogService.EntitlementRule(
                                "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", new BigDecimal("6000"),
                                "SECONDS", false, new BigDecimal("60"), 149),
                        new CommercialPlanCatalogService.EntitlementRule(
                                "CONCURRENT_CALLS", "CAPACITY", null, BigDecimal.ONE,
                                "COUNT", true, null, null)));

        when(subscriptions.findByExternalSubscriptionId(SUBSCRIPTION_ID)).thenReturn(Optional.of(local));
        when(subscriptions.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(local));
        when(catalog.requireByCode("BASIC")).thenReturn(emprende);

        BillingSubscriptionService billing = new BillingSubscriptionService(
                subscriptions, realGateway, properties, jdbc, catalog);

        billing.reconcileAuthorizedPayment(INVOICE_ID);

        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("BASIC", local.getPlanCode());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getBillingCheckoutUrl());
        assertEquals(INVOICE_ID, local.getLastBillingInvoiceId());
        assertEquals("approved", local.getLastBillingPaymentStatus());
        assertNotNull(local.getCurrentPeriodStart());
        assertNotNull(local.getCurrentPeriodEnd());

        Instant firstStart = local.getCurrentPeriodStart();
        Instant firstEnd = local.getCurrentPeriodEnd();

        billing.reconcileAuthorizedPayment(INVOICE_ID);

        assertEquals(firstStart, local.getCurrentPeriodStart());
        assertEquals(firstEnd, local.getCurrentPeriodEnd());
        verify(subscriptions, times(1)).saveAndFlush(local);

        NamedParameterJdbcTemplate usageJdbc = mock(NamedParameterJdbcTemplate.class);
        when(usageJdbc.queryForObject(
                contains("FROM usage_meter_event"),
                any(MapSqlParameterSource.class),
                eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);

        CommercialEntitlementService entitlements =
                new CommercialEntitlementService(subscriptions, catalog, usageJdbc);
        var snapshot = entitlements.snapshot(BUSINESS_ID);

        assertEquals("EMPRENDE", snapshot.publicPlanCode());
        assertEquals("ACTIVE", snapshot.status());
        assertTrue(snapshot.serviceAllowed());
        assertEquals(new BigDecimal("6000"), snapshot.requireEntitlement("VOICE_SECONDS").limit());
        assertEquals(1, entitlements.capacity(BUSINESS_ID, "CONCURRENT_CALLS"));

        System.out.println("RECEPVOZ_SANDBOX_BRIDGE=PASS");
        System.out.println("RECEPVOZ_SUBSCRIPTION_STATUS=" + local.getStatus());
        System.out.println("RECEPVOZ_PLAN=" + snapshot.publicPlanCode());
        System.out.println("RECEPVOZ_INVOICE=" + local.getLastBillingInvoiceId());
        System.out.println("RECEPVOZ_IDEMPOTENT_SAVE_COUNT=1");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) fail(name + " is required");
        return value.trim();
    }
}
