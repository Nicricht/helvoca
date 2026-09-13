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
    void checkoutKeepsCurrentPlanUntilProviderReconciliationConfirmsAuthorization() {
        BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setBackUrl("https://example.test/billing/return");

        UUID businessId = UUID.randomUUID();
        BusinessSubscription local = new BusinessSubscription();
        local.setBusinessId(businessId);
        local.setPlanCode(PlanCode.BASIC);
        local.setStatus(SubscriptionStatus.ACTIVE);
        local.setCurrentPeriodStart(Instant.now().minusSeconds(60));
        local.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));

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

        OffsetDateTime nextPayment = OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1);
        when(gateway.getSubscription("pre-1"))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        "pre-1", "authorized", "helvoca:" + businessId + ":PRO", nextPayment));
        when(subscriptions.findByExternalSubscriptionId("pre-1")).thenReturn(Optional.of(local));

        service.reconcileSubscription("pre-1");

        assertEquals(PlanCode.PRO, local.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, local.getStatus());
        assertNull(local.getPendingPlanCode());
        assertNull(local.getBillingCheckoutUrl());
        verify(gateway).createCheckout(businessId, "owner@example.test", PlanCode.PRO);
        verify(gateway).getSubscription("pre-1");
    }
}
