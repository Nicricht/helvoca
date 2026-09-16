package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingSubscriptionServiceTest {

    @Test
    void checkoutAndSubscriptionAuthorizationKeepCurrentPlanUntilApprovedInvoice() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        var basic = plan("BASIC", "EMPRENDE", "Emprende", 24_990, false);
        var negocio = plan("PRO", "NEGOCIO", "Negocio", 39_990, false);
        when(catalog.findActiveByPublicCode("NEGOCIO")).thenReturn(negocio);
        when(catalog.requireByCode("BASIC")).thenReturn(basic);
        when(catalog.requireByCode("PRO")).thenReturn(negocio);
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        PaymentPlan paymentPlan = new PaymentPlan("PRO", "Negocio", 39_990, false);
        when(gateway.createCheckout(businessId, "owner@example.test", paymentPlan))
                .thenReturn(new SubscriptionPaymentGateway.Checkout(
                        "pre-1", "https://checkout.example.test/pre-1", "pending",
                        "helvoca:" + businessId + ":PRO"));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc, catalog);
        var checkout = service.createCheckout(businessId, "owner@example.test", "NEGOCIO");

        assertEquals("BASIC", local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("PRO", local.getPendingPlanCode());
        assertEquals("pre-1", local.getExternalSubscriptionId());
        assertEquals("NEGOCIO", checkout.planCode());
        assertFalse(checkout.reused());

        when(gateway.getSubscription("pre-1"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-1", "authorized", "helvoca:" + businessId + ":PRO",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1)));
        when(subscriptions.findByExternalSubscriptionId("pre-1")).thenReturn(Optional.of(local));

        service.reconcileSubscription("pre-1");
        assertEquals("BASIC", local.getPlanCode());
        assertEquals("PRO", local.getPendingPlanCode());

        when(gateway.getInvoice("invoice-1"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-1", "pre-1", "approved", "", OffsetDateTime.now(ZoneOffset.UTC)));
        service.reconcileAuthorizedPayment("invoice-1");

        assertEquals("PRO", local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getBillingCheckoutUrl());
        verify(gateway).createCheckout(businessId, "owner@example.test", paymentPlan);
    }

    @Test
    void customPricingPlanIsRejectedBeforeGatewayInvocation() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        when(catalog.findActiveByPublicCode("ENTERPRISE"))
                .thenReturn(plan("ENTERPRISE", "ENTERPRISE", "Enterprise", 119_990, true));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        assertThrows(IllegalArgumentException.class,
                () -> service.createCheckout(UUID.randomUUID(), "owner@example.test", "ENTERPRISE"));
        verifyNoInteractions(gateway);
        verifyNoInteractions(jdbc);
    }

    @Test
    void manualRefreshDoesNotActivateWhileProviderStillReportsPending() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-pending");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-pending");
        stubCatalog(catalog);

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-pending"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-pending", "pending", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc, catalog);
        var status = service.refresh(businessId);

        assertEquals("BASIC", local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("PRO", local.getPendingPlanCode());
        assertTrue(status.awaitingProviderVerification());
        assertEquals("NEGOCIO", status.pendingPlanCode());
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void subscriptionAuthorizationStillRequiresApprovedInvoice() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-authorized");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-authorized");
        stubCatalog(catalog);

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(subscriptions.findByExternalSubscriptionId("pre-authorized")).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-authorized"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-authorized", "authorized", "helvoca:" + businessId + ":PRO",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1)));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc, catalog);
        var status = service.refresh(businessId);

        assertEquals("BASIC", local.getPlanCode());
        assertEquals("PRO", local.getPendingPlanCode());
        assertTrue(status.awaitingProviderVerification());
        verify(subscriptions, never()).saveAndFlush(local);

        when(gateway.getInvoice("invoice-authorized"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-authorized", "pre-authorized", "processed", "",
                        OffsetDateTime.now(ZoneOffset.UTC)));
        service.reconcileAuthorizedPayment("invoice-authorized");

        assertEquals("PRO", local.getPlanCode());
        assertNull(local.getPendingPlanCode());
        assertFalse(service.status(businessId).awaitingProviderVerification());
        verify(subscriptions).saveAndFlush(local);
    }

    @Test
    void mismatchedRemoteSubscriptionIdFailsClosed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-expected");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-expected");

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-expected"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-other", "authorized", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        assertThrows(IllegalStateException.class, () -> service.refresh(businessId));
        assertEquals("BASIC", local.getPlanCode());
        assertEquals("PRO", local.getPendingPlanCode());
        verify(subscriptions, never()).saveAndFlush(local);
    }

    private static void stubCatalog(CommercialPlanCatalogService catalog) {
        when(catalog.requireByCode("BASIC")).thenReturn(plan("BASIC", "EMPRENDE", "Emprende", 24_990, false));
        when(catalog.requireByCode("PRO")).thenReturn(plan("PRO", "NEGOCIO", "Negocio", 39_990, false));
    }

    private static CommercialPlanCatalogService.Plan plan(String code, String publicCode, String name,
                                                           Integer price, boolean customPricing) {
        return new CommercialPlanCatalogService.Plan(
                code, publicCode, name, price, "CLP", customPricing, false, true, 1, List.of());
    }

    private static MercadoPagoProperties configuredProperties() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setBackUrl("https://example.test/billing/return");
        return properties;
    }

    private static BusinessSubscription activeBasic(UUID businessId) {
        BusinessSubscription local = new BusinessSubscription();
        local.setBusinessId(businessId);
        local.setPlanCode("BASIC");
        local.setStatus(SubscriptionStatus.ACTIVE);
        local.setCurrentPeriodStart(Instant.now().minusSeconds(60));
        local.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        return local;
    }
}
