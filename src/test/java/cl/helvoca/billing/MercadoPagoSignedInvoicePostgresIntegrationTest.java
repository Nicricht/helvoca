package cl.helvoca.billing;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.testsupport.ExplicitSystemDatabaseScopeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * C5-A: real PostgreSQL (Flyway + RLS), actual signed SDK HMAC, actual
 * transactional billing service and persisted plan state. Remote Mercado Pago
 * API is the ONLY mocked boundary; no production database or payment.
 *
 * A passing test certifies our isolated stack, NOT webhook delivery by MP.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.outbound.delivery-enabled=false",
        "app.outbound.provider=NONE",
        "app.twilio.provisioning-enabled=false",
        "app.mercadopago.enabled=true",
        "app.mercadopago.access-token=offline-test-only",
        "app.mercadopago.webhook-secret=c5-only-local-fake-webhook-key",
        "app.mercadopago.back-url=https://example.invalid/return"
})
class MercadoPagoSignedInvoicePostgresIntegrationTest extends ExplicitSystemDatabaseScopeSupport {
    private static final String SECRET = MercadoPagoWebhookSignatureRoundTripTest.SECRET;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired BusinessRepository businesses;
    @Autowired BusinessSubscriptionRepository subscriptions;
    @Autowired MercadoPagoWebhookController webhook;

    @MockitoBean SubscriptionPaymentGateway remoteProvider;

    private BusinessSubscription businessSubscription(String label, String externalId, boolean pending) {
        Business business = new Business();
        business.setName("C5 isolated " + label + " " + UUID.randomUUID());
        business = businesses.saveAndFlush(business);

        BusinessSubscription sub = new BusinessSubscription();
        sub.setBusinessId(business.getId());
        sub.setPlanCode("BASIC");
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(Instant.parse("2026-09-01T00:00:00Z"));
        sub.setCurrentPeriodEnd(Instant.parse("2026-10-01T00:00:00Z"));
        sub.setBillingProvider("mercadopago");
        sub.setExternalSubscriptionId(externalId);
        if (pending) {
            sub.setPendingPlanCode("PRO");
            sub.setBillingCheckoutUrl("https://www.mercadopago.cl/subscriptions/checkout");
        }
        return subscriptions.saveAndFlush(sub);
    }

    private BusinessSubscription reload(UUID businessId) {
        return subscriptions.findByBusinessId(businessId).orElseThrow();
    }

    private int signedInvoice(String invoiceId, String requestId, String signatureOverride) {
        String signature = signatureOverride == null
                ? MercadoPagoWebhookSignatureRoundTripTest.sign(invoiceId, requestId)
                : signatureOverride;
        return webhook.webhook(signature, requestId, "subscription_authorized_payment",
                null, invoiceId, null).getStatusCode().value();
    }

    @Test
    void onlySignedAndApprovedRemoteInvoiceUpgradesCorrectTenantInPostgresExactlyOnce() {
        String idA = "c5-sandbox-a-" + UUID.randomUUID();
        String idB = "c5-sandbox-b-" + UUID.randomUUID();
        BusinessSubscription a = businessSubscription("A", idA, true);
        BusinessSubscription b = businessSubscription("B", idB, true);
        UUID tenantA = a.getBusinessId();
        UUID tenantB = b.getBusinessId();
        String invoice = "invoice-c5-" + UUID.randomUUID();

        when(remoteProvider.getInvoice(invoice))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        invoice, idA, "processed", "approved", "",
                        OffsetDateTime.parse("2026-10-14T10:00:00Z")));

        assertEquals(204, signedInvoice(invoice, "req-c5-approved", null));
        BusinessSubscription paidA = reload(tenantA);
        assertEquals("PRO", paidA.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, paidA.getStatus());
        assertNull(paidA.getPendingPlanCode());
        assertNull(paidA.getBillingCheckoutUrl());
        assertEquals(invoice, paidA.getLastBillingInvoiceId());
        assertEquals("approved", paidA.getLastBillingPaymentStatus());
        Instant firstEnd = paidA.getCurrentPeriodEnd();
        Instant firstStart = paidA.getCurrentPeriodStart();

        BusinessSubscription stillB = reload(tenantB);
        assertEquals("BASIC", stillB.getPlanCode());
        assertEquals("PRO", stillB.getPendingPlanCode());
        assertNull(stillB.getLastBillingInvoiceId());

        assertEquals(204, signedInvoice(invoice, "req-c5-replay", null));
        BusinessSubscription replayedA = reload(tenantA);
        assertEquals(firstStart, replayedA.getCurrentPeriodStart());
        assertEquals(firstEnd, replayedA.getCurrentPeriodEnd());
        assertEquals("PRO", replayedA.getPlanCode());
        assertEquals("BASIC", reload(tenantB).getPlanCode());
        verify(remoteProvider, times(2)).getInvoice(invoice);
        verifyNoMoreInteractions(remoteProvider);
    }

    @Test
    void validPreapprovalWebhookDoesNotActivateEntitlementsWithoutPaidInvoice() {
        String external = "pre-c5-" + UUID.randomUUID();
        BusinessSubscription a = businessSubscription("preapproval", external, true);
        when(remoteProvider.getSubscription(external))
                .thenReturn(new SubscriptionPaymentGateway.RemoteSubscription(
                        external, "authorized", "helvoca:" + a.getBusinessId() + ":PRO", null));
        int response = webhook.webhook(
                MercadoPagoWebhookSignatureRoundTripTest.sign(external, "req-preapproval"),
                "req-preapproval", "subscription_preapproval", null, external, null
        ).getStatusCode().value();
        assertEquals(204, response);
        assertEquals("BASIC", reload(a.getBusinessId()).getPlanCode());
        assertEquals("PRO", reload(a.getBusinessId()).getPendingPlanCode());
        assertNull(reload(a.getBusinessId()).getLastBillingInvoiceId());
        verify(remoteProvider).getSubscription(external);
        verifyNoMoreInteractions(remoteProvider);
    }

    @Test
    void forgedOrTamperedInvoiceRequestCannotReadProviderOrModifyPostgres() {
        String external = "pre-c5-forge-" + UUID.randomUUID();
        BusinessSubscription a = businessSubscription("forgery", external, true);
        assertEquals(401, signedInvoice("invoice-fake", "req-forged", "ts=1,v1=bad"));
        String header = MercadoPagoWebhookSignatureRoundTripTest.sign("invoice-original", "req-original");
        assertEquals(401, signedInvoice("invoice-tampered", "req-original", header));
        assertEquals("BASIC", reload(a.getBusinessId()).getPlanCode());
        assertEquals("PRO", reload(a.getBusinessId()).getPendingPlanCode());
        verifyNoInteractions(remoteProvider);
    }

    @Test
    void signedInvoiceBelongingToUnknownRemoteSubscriptionDoesNotChangeTenant() {
        String external = "pre-c5-valid-" + UUID.randomUUID();
        BusinessSubscription a = businessSubscription("unknown-owner", external, true);
        String invoice = "invoice-c5-unlinked-" + UUID.randomUUID();
        when(remoteProvider.getInvoice(invoice))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        invoice, "another-subs-id", "processed", "approved", "",
                        OffsetDateTime.now(ZoneOffset.UTC)));
        assertEquals(500, signedInvoice(invoice, "req-unknown-owner", null));
        assertEquals("BASIC", reload(a.getBusinessId()).getPlanCode());
        assertNull(reload(a.getBusinessId()).getLastBillingInvoiceId());
        verify(remoteProvider).getInvoice(invoice);
        verifyNoMoreInteractions(remoteProvider);
    }

    @Test
    void repeatedRejectedPaymentDoesNotExtendGraceAndNeverUpgradesPlan() {
        String external = "pre-c5-rejected-" + UUID.randomUUID();
        BusinessSubscription a = businessSubscription("rejected", external, true);
        String invoice = "invoice-c5-rejected-" + UUID.randomUUID();
        when(remoteProvider.getInvoice(invoice))
                .thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                        invoice, external, "processed", "rejected", "",
                        OffsetDateTime.now(ZoneOffset.UTC)));
        assertEquals(204, signedInvoice(invoice, "req-reject-1", null));
        BusinessSubscription first = reload(a.getBusinessId());
        assertEquals(SubscriptionStatus.PAST_DUE, first.getStatus());
        assertEquals("BASIC", first.getPlanCode());
        Instant grace = first.getGraceUntil();
        assertNotNull(grace);
        assertEquals(204, signedInvoice(invoice, "req-reject-replay", null));
        BusinessSubscription replay = reload(a.getBusinessId());
        assertEquals(grace, replay.getGraceUntil());
        assertEquals("BASIC", replay.getPlanCode());
        assertEquals("PRO", replay.getPendingPlanCode());
        verify(remoteProvider, times(2)).getInvoice(invoice);
        verifyNoMoreInteractions(remoteProvider);
    }
}
