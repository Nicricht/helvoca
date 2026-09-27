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
                        "invoice-1", "pre-1", "processed", "approved", "", OffsetDateTime.now(ZoneOffset.UTC)));
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
                        "invoice-authorized", "pre-authorized", "processed", "approved", "",
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


    @Test
    void checkoutFailsClosedWhenProviderReturnsUnexpectedExternalReference() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        var negocio = plan("PRO", "NEGOCIO", "Negocio", 39_990, false);

        when(catalog.findActiveByPublicCode("NEGOCIO")).thenReturn(negocio);
        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.createCheckout(eq(businessId), eq("owner@example.test"), any(PaymentPlan.class)))
                .thenReturn(new SubscriptionPaymentGateway.Checkout(
                        "pre-wrong", "https://checkout.example.test/pre-wrong", "pending", "unexpected-reference"));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        assertThrows(IllegalStateException.class,
                () -> service.createCheckout(businessId, "owner@example.test", "NEGOCIO"));
        assertNull(local.getPendingPlanCode());
        assertNull(local.getExternalSubscriptionId());
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void duplicateApprovedInvoiceIsAppliedOnlyOnce() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-dup");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-dup");
        OffsetDateTime debitDate = OffsetDateTime.of(2026, 9, 27, 18, 0, 0, 0, ZoneOffset.UTC);

        when(subscriptions.findByExternalSubscriptionId("pre-dup")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-dup"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-dup", "pre-dup", "processed", "approved", "", debitDate));
        when(catalog.requireByCode("PRO")).thenReturn(plan("PRO", "NEGOCIO", "Negocio", 39_990, false));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        service.reconcileAuthorizedPayment("invoice-dup");
        Instant firstPeriodEnd = local.getCurrentPeriodEnd();
        service.reconcileAuthorizedPayment("invoice-dup");

        assertEquals("invoice-dup", local.getLastBillingInvoiceId());
        assertEquals("approved", local.getLastBillingPaymentStatus());
        assertEquals("PRO", local.getPlanCode());
        assertEquals(debitDate.toInstant(), local.getCurrentPeriodStart());
        assertEquals(firstPeriodEnd, local.getCurrentPeriodEnd());
        verify(subscriptions, times(1)).saveAndFlush(local);
    }

    @Test
    void duplicateRejectedInvoiceDoesNotExtendGraceWindow() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setExternalSubscriptionId("pre-rejected");

        when(subscriptions.findByExternalSubscriptionId("pre-rejected")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-rejected"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-rejected", "pre-rejected", "recycling", "rejected", "", OffsetDateTime.now(ZoneOffset.UTC)));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        service.reconcileAuthorizedPayment("invoice-rejected");
        Instant firstGraceUntil = local.getGraceUntil();
        service.reconcileAuthorizedPayment("invoice-rejected");

        assertEquals(SubscriptionStatus.PAST_DUE, local.getStatus());
        assertEquals("invoice-rejected", local.getLastBillingInvoiceId());
        assertEquals("rejected", local.getLastBillingPaymentStatus());
        assertEquals(firstGraceUntil, local.getGraceUntil());
        verify(subscriptions, times(1)).saveAndFlush(local);
    }

    @Test
    void approvedRenewalRefreshesPeriodWithoutChangingPlan() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setExternalSubscriptionId("pre-renew");
        OffsetDateTime debitDate = OffsetDateTime.of(2026, 10, 27, 12, 0, 0, 0, ZoneOffset.UTC);

        when(subscriptions.findByExternalSubscriptionId("pre-renew")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-renew"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-renew", "pre-renew", "processed", "approved", "", debitDate));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        service.reconcileAuthorizedPayment("invoice-renew");

        assertEquals("BASIC", local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals(debitDate.toInstant(), local.getCurrentPeriodStart());
        assertEquals(debitDate.plusMonths(1).toInstant(), local.getCurrentPeriodEnd());
        assertEquals("invoice-renew", local.getLastBillingInvoiceId());
        assertEquals("approved", local.getLastBillingPaymentStatus());
    }

    @Test
    void processedInvoiceWithoutApprovedPaymentCannotActivatePlan() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-no-payment");

        when(subscriptions.findByExternalSubscriptionId("pre-no-payment")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-no-payment"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-no-payment", "pre-no-payment", "processed", null, "",
                        OffsetDateTime.now(ZoneOffset.UTC)));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        assertThrows(IllegalStateException.class,
                () -> service.reconcileAuthorizedPayment("invoice-no-payment"));
        assertEquals("BASIC", local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("PRO", local.getPendingPlanCode());
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void sameInvoiceCanRecoverFromRejectedPaymentToApprovedPayment() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-recover");
        OffsetDateTime debitDate = OffsetDateTime.of(2026, 9, 27, 18, 30, 0, 0, ZoneOffset.UTC);

        when(subscriptions.findByExternalSubscriptionId("pre-recover")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-recover"))
                .thenReturn(
                        new SubscriptionPaymentGateway.RemoteInvoice(
                                "invoice-recover", "pre-recover", "recycling", "rejected", "", debitDate),
                        new SubscriptionPaymentGateway.RemoteInvoice(
                                "invoice-recover", "pre-recover", "processed", "approved", "", debitDate));
        when(catalog.requireByCode("PRO")).thenReturn(plan("PRO", "NEGOCIO", "Negocio", 39_990, false));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        service.reconcileAuthorizedPayment("invoice-recover");
        Instant graceAfterRejection = local.getGraceUntil();

        assertEquals(SubscriptionStatus.PAST_DUE, local.getStatus());
        assertEquals("rejected", local.getLastBillingPaymentStatus());
        assertNotNull(graceAfterRejection);

        service.reconcileAuthorizedPayment("invoice-recover");

        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("PRO", local.getPlanCode());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getGraceUntil());
        assertEquals("approved", local.getLastBillingPaymentStatus());
        verify(subscriptions, times(2)).saveAndFlush(local);
    }

    @Test
    void canceledRemoteSubscriptionCancelsLocalAccessAndPendingChange() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-cancel");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-cancel");

        when(subscriptions.findByExternalSubscriptionId("pre-cancel")).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-cancel"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-cancel", "cancelled", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = new BillingSubscriptionService(
                subscriptions, gateway, configuredProperties(), jdbc, catalog);

        service.reconcileSubscription("pre-cancel");

        assertEquals(SubscriptionStatus.CANCELED, local.getStatus());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getBillingCheckoutUrl());
        verify(subscriptions).saveAndFlush(local);
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
