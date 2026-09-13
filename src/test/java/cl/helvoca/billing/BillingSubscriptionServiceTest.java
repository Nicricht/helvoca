package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.createCheckout(businessId, "owner@example.test", PlanCode.PRO))
                .thenReturn(new SubscriptionPaymentGateway.Checkout(
                        "pre-1", "https://checkout.example.test/pre-1", "pending",
                        "helvoca:" + businessId + ":PRO"));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc);
        var checkout = service.createCheckout(businessId, "owner@example.test", "NEGOCIO");

        assertEquals(PlanCode.BASIC, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals(PlanCode.PRO, local.getPendingPlanCode());
        assertEquals("pre-1", local.getExternalSubscriptionId());
        assertFalse(checkout.reused());

        when(gateway.getSubscription("pre-1"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-1", "authorized", "helvoca:" + businessId + ":PRO",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1)));
        when(subscriptions.findByExternalSubscriptionId("pre-1")).thenReturn(Optional.of(local));

        service.reconcileSubscription("pre-1");

        assertEquals(PlanCode.BASIC, local.getPlanCode());
        assertEquals(PlanCode.PRO, local.getPendingPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());

        when(gateway.getInvoice("invoice-1"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-1", "pre-1", "approved", "", OffsetDateTime.now(ZoneOffset.UTC)));

        service.reconcileAuthorizedPayment("invoice-1");

        assertEquals(PlanCode.PRO, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getBillingCheckoutUrl());
        verify(gateway).createCheckout(businessId, "owner@example.test", PlanCode.PRO);
        verify(gateway).getSubscription("pre-1");
        verify(gateway).getInvoice("invoice-1");
    }

    @Test
    void manualRefreshDoesNotActivateWhileProviderStillReportsPending() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode(PlanCode.PRO);
        local.setExternalSubscriptionId("pre-pending");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-pending");

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-pending"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-pending", "pending", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc);
        var status = service.refresh(businessId);

        assertEquals(PlanCode.BASIC, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals(PlanCode.PRO, local.getPendingPlanCode());
        assertTrue(status.awaitingProviderVerification());
        assertEquals("NEGOCIO", status.pendingPlanCode());
        verify(gateway).getSubscription("pre-pending");
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void subscriptionAuthorizationStillRequiresApprovedInvoice() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode(PlanCode.PRO);
        local.setExternalSubscriptionId("pre-authorized");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-authorized");

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(subscriptions.findByExternalSubscriptionId("pre-authorized")).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-authorized"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-authorized", "authorized", "helvoca:" + businessId + ":PRO",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1)));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc);
        var status = service.refresh(businessId);

        assertEquals(PlanCode.BASIC, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals(PlanCode.PRO, local.getPendingPlanCode());
        assertTrue(status.awaitingProviderVerification());
        verify(subscriptions, never()).saveAndFlush(local);

        when(gateway.getInvoice("invoice-authorized"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-authorized", "pre-authorized", "processed", "",
                        OffsetDateTime.now(ZoneOffset.UTC)));

        service.reconcileAuthorizedPayment("invoice-authorized");

        assertEquals(PlanCode.PRO, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertNull(local.getPendingPlanCode());
        assertFalse(service.status(businessId).awaitingProviderVerification());
        verify(subscriptions).saveAndFlush(local);
    }

    @Test
    void mismatchedRemoteSubscriptionIdFailsClosed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MercadoPagoProperties properties = configuredProperties();

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setBillingProvider("mercadopago");
        local.setPendingPlanCode(PlanCode.PRO);
        local.setExternalSubscriptionId("pre-expected");
        local.setBillingCheckoutUrl("https://checkout.example.test/pre-expected");

        when(subscriptions.findByBusinessId(businessId)).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-expected"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-other", "authorized", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = new BillingSubscriptionService(subscriptions, gateway, properties, jdbc);

        assertThrows(IllegalStateException.class, () -> service.refresh(businessId));
        assertEquals(PlanCode.BASIC, local.getPlanCode());
        assertEquals(PlanCode.PRO, local.getPendingPlanCode());
        verify(subscriptions, never()).saveAndFlush(local);
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
        local.setPlanCode(PlanCode.BASIC);
        local.setStatus(SubscriptionStatus.ACTIVE);
        local.setCurrentPeriodStart(Instant.now().minusSeconds(60));
        local.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        return local;
    }
}
