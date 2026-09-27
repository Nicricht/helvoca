package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingSubscriptionServiceEdgeCasesTest {

    @Test
    void blankInvoiceIdIsRejectedBeforeProviderLookup() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));

        assertThrows(IllegalArgumentException.class, () -> service.reconcileAuthorizedPayment("   "));
        verifyNoInteractions(gateway, subscriptions);
    }

    @Test
    void invoiceWithoutSubscriptionIdIsRejectedBeforeLocalMutation() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        when(gateway.getInvoice("invoice-no-subscription"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-no-subscription", " ", "processed", "approved", "", null));

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.reconcileAuthorizedPayment("invoice-no-subscription"));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void unknownInvoiceSubscriptionFailsClosed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        when(gateway.getInvoice("invoice-unknown"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-unknown", "pre-unknown", "processed", "approved", "", null));
        when(subscriptions.findByExternalSubscriptionId("pre-unknown")).thenReturn(Optional.empty());

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.reconcileAuthorizedPayment("invoice-unknown"));
        verify(subscriptions, never()).saveAndFlush(any());
    }

    @Test
    void mismatchedSubscriptionReferenceFailsClosed() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setExternalSubscriptionId("pre-reference");
        when(subscriptions.findByExternalSubscriptionId("pre-reference")).thenReturn(Optional.of(local));
        when(gateway.getSubscription("pre-reference"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-reference", "authorized", "helvoca:" + businessId + ":PRO", null));

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));

        assertThrows(IllegalStateException.class, () -> service.reconcileSubscription("pre-reference"));
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void approvedInvoiceWithoutDebitDateUsesARealCurrentPeriod() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setExternalSubscriptionId("pre-now");
        when(subscriptions.findByExternalSubscriptionId("pre-now")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-now"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-now", "pre-now", "processed", "approved", "", null));

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));
        Instant before = Instant.now();
        service.reconcileAuthorizedPayment("invoice-now");
        Instant after = Instant.now();

        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertFalse(local.getCurrentPeriodStart().isBefore(before));
        assertFalse(local.getCurrentPeriodStart().isAfter(after));
        assertTrue(local.getCurrentPeriodEnd().isAfter(local.getCurrentPeriodStart()));
        assertEquals("approved", local.getLastBillingPaymentStatus());
        verify(subscriptions).saveAndFlush(local);
    }

    @Test
    void pendingPaymentStatusDoesNotActivateOrMarkPastDue() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setPendingPlanCode("PRO");
        local.setExternalSubscriptionId("pre-payment-pending");
        when(subscriptions.findByExternalSubscriptionId("pre-payment-pending")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-payment-pending"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-payment-pending", "pre-payment-pending", "scheduled", "pending", "", null));

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));
        service.reconcileAuthorizedPayment("invoice-payment-pending");

        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertEquals("BASIC", local.getPlanCode());
        assertEquals("PRO", local.getPendingPlanCode());
        assertNull(local.getLastBillingInvoiceId());
        verify(subscriptions, never()).saveAndFlush(local);
    }

    @Test
    void britishSpellingCancelledPaymentMarksPastDue() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = activeBasic(businessId);
        local.setExternalSubscriptionId("pre-cancelled");
        when(subscriptions.findByExternalSubscriptionId("pre-cancelled")).thenReturn(Optional.of(local));
        when(gateway.getInvoice("invoice-cancelled"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        "invoice-cancelled", "pre-cancelled", "processed", "cancelled", "", null));

        BillingSubscriptionService service = service(subscriptions, gateway, mock(CommercialPlanCatalogService.class));
        service.reconcileAuthorizedPayment("invoice-cancelled");

        assertEquals(SubscriptionStatus.PAST_DUE, local.getStatus());
        assertEquals("cancelled", local.getLastBillingPaymentStatus());
        assertNotNull(local.getGraceUntil());
        verify(subscriptions).saveAndFlush(local);
    }

    private static BillingSubscriptionService service(BusinessSubscriptionRepository subscriptions,
                                                      SubscriptionPaymentGateway gateway,
                                                      CommercialPlanCatalogService catalog) {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setBackUrl("https://example.test/billing/return");
        return new BillingSubscriptionService(subscriptions, gateway, properties, mock(JdbcTemplate.class), catalog);
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
