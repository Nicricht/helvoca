package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class BillingProviderPostgresBridgeIntegrationTest {
    // Real isolated PostgreSQL with migrations and RLS; NEVER contact Mercado Pago.
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.mercadopago.enabled", () -> "false");
    }

    @MockitoBean
    SubscriptionPaymentGateway provider;

    @Autowired
    BillingSubscriptionService billing;

    @Autowired
    CommercialEntitlementService entitlements;

    @Autowired
    TenantDatabaseContext scope;

    @Autowired
    @Qualifier("migrationDataSource")
    DataSource migrationDataSource;

    private JdbcTemplate owner;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        owner = new JdbcTemplate(migrationDataSource);
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        for (UUID businessId : new UUID[]{tenantA, tenantB}) {
            owner.update("INSERT INTO public.business(id, name) VALUES (?, ?)",
                    businessId, "C5 isolated provider invoice bridge");
            owner.update("""
                    INSERT INTO public.business_subscription (
                        id, business_id, plan_code, status, current_period_start,
                        current_period_end, pending_plan_code, billing_provider,
                        external_subscription_id, billing_checkout_url, created_at, updated_at
                    ) VALUES (?, ?, 'BASIC', 'TRIALING', ?, ?, 'PRO',
                              'mercadopago', ?, 'https://checkout.example.test/pending',
                              now(), now())
                    """,
                    UUID.randomUUID(), businessId,
                    Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")),
                    Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")),
                    subscriptionId(businessId));
        }
    }

    @AfterEach
    void tearDown() {
        if (owner != null && tenantA != null && tenantB != null) {
            owner.update("DELETE FROM public.business WHERE id IN (?, ?)", tenantA, tenantB);
        }
    }

    private static String subscriptionId(UUID businessId) {
        return "pre-" + businessId;
    }

    private Map<String, Object> stored(UUID businessId) {
        return owner.queryForMap("""
                SELECT plan_code, status, pending_plan_code, grace_until,
                       last_billing_invoice_id, last_billing_payment_status,
                       billing_checkout_url, current_period_start, current_period_end,
                       updated_at
                  FROM public.business_subscription WHERE business_id = ?
                """, businessId);
    }

    @Test
    void approvedProviderInvoicePersistsAndChangesOnlyMatchingTenantEntitlements() {
        String invoiceId = "test-invoice-approved-a";
        OffsetDateTime paidAt = OffsetDateTime.of(2026, 10, 13, 19, 0, 0, 0, ZoneOffset.UTC);
        when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                invoiceId, subscriptionId(tenantA), "processed", "approved", "paid", paidAt));

        scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));

        Map<String, Object> a = stored(tenantA);
        Map<String, Object> b = stored(tenantB);
        assertEquals("PRO", a.get("plan_code"));
        assertEquals("ACTIVE", a.get("status"));
        assertNull(a.get("pending_plan_code"));
        assertNull(a.get("billing_checkout_url"));
        assertEquals(invoiceId, a.get("last_billing_invoice_id"));
        assertEquals("approved", a.get("last_billing_payment_status"));
        assertEquals(paidAt.toInstant(), ((Timestamp) a.get("current_period_start")).toInstant());
        assertTrue(((Timestamp) a.get("current_period_end")).toInstant().isAfter(paidAt.toInstant()));

        // Two businesses have separate durable rows and distinct entitlements.
        assertEquals("BASIC", b.get("plan_code"));
        assertEquals("TRIALING", b.get("status"));
        assertNull(b.get("last_billing_invoice_id"));
        assertEquals("PRO", b.get("pending_plan_code"));
        var allowed = scope.callAsSystem(() -> entitlements.snapshot(tenantA));
        assertEquals("NEGOCIO", allowed.publicPlanCode());
        assertEquals("ACTIVE", allowed.status());
        assertTrue(allowed.serviceAllowed());
        assertEquals(3, scope.callAsSystem(
                () -> entitlements.capacity(tenantA, "CONCURRENT_CALLS")));

        // Persisted idempotency, not merely an in-memory mock invocation.
        scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));
        assertEquals(a, stored(tenantA));
        assertEquals(b, stored(tenantB));
        verify(provider, times(2)).getInvoice(invoiceId);
    }

    @Test
    void otherTenantCannotReconcileAnInvoiceBelongingToTenantA() {
        String invoiceId = "test-invoice-cross-tenant";
        when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                invoiceId, subscriptionId(tenantA), "processed", "approved", "paid",
                OffsetDateTime.parse("2026-10-13T19:00:00Z")));

        // An authenticated provider ingress uses explicitly SYSTEM-scoped access.
        // Tenant B must not discover or mutate tenant A's invoice/plan via RLS.
        assertThrows(RuntimeException.class, () ->
                scope.runAsTenant(tenantB, () -> billing.reconcileAuthorizedPayment(invoiceId)));
        assertEquals("TRIALING", stored(tenantA).get("status"));
        assertEquals("TRIALING", stored(tenantB).get("status"));
        assertNull(stored(tenantA).get("last_billing_invoice_id"));
    }

    @Test
    void rejectedInvoiceDuplicateKeepsOriginalGraceAndSameInvoiceCanRecoverApproved() {
        String invoiceId = "test-invoice-recovery";
        when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                invoiceId, subscriptionId(tenantA), "processed", "rejected", "rejected",
                OffsetDateTime.parse("2026-10-13T19:00:00Z")));
        scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));
        Map<String, Object> rejected = stored(tenantA);
        assertEquals("PAST_DUE", rejected.get("status"));
        assertNotNull(rejected.get("grace_until"));
        assertEquals("BASIC", rejected.get("plan_code"));
        assertEquals("PRO", rejected.get("pending_plan_code"));

        scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));
        assertEquals(rejected, stored(tenantA));

        when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                invoiceId, subscriptionId(tenantA), "processed", "approved", "paid",
                OffsetDateTime.parse("2026-10-13T19:00:00Z")));
        scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));
        Map<String, Object> recovered = stored(tenantA);
        assertEquals("ACTIVE", recovered.get("status"));
        assertEquals("PRO", recovered.get("plan_code"));
        assertNull(recovered.get("pending_plan_code"));
        assertNull(recovered.get("grace_until"));
        assertEquals("approved", recovered.get("last_billing_payment_status"));
        assertEquals("TRIALING", stored(tenantB).get("status"));
    }

    @Test
    void preapprovalAuthorizedWithoutApprovedInvoiceNeverActivatesPlan() {
        String preapproval = subscriptionId(tenantA);
        when(provider.getSubscription(preapproval)).thenReturn(
                new SubscriptionPaymentGateway.RemoteSubscription(
                        preapproval, "authorized", "helvoca:" + tenantA + ":PRO", null));

        scope.runAsSystem(() -> billing.reconcileSubscription(preapproval));
        Map<String, Object> unchanged = stored(tenantA);
        assertEquals("TRIALING", unchanged.get("status"));
        assertEquals("BASIC", unchanged.get("plan_code"));
        assertEquals("PRO", unchanged.get("pending_plan_code"));
        assertNull(unchanged.get("last_billing_invoice_id"));
    }
}
