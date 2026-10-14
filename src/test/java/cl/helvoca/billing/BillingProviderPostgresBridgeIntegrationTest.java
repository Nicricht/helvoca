package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    WebApplicationContext webContext;

    @Autowired
    MercadoPagoProperties webhookProperties;

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
    void parallelDuplicateApprovedInvoiceAppliesExactlyOneDurablePostgresUpdate() throws Exception {
        String invoiceId = "test-invoice-concurrent";
        // Instrument real DB writes in the temporary Testcontainers database only.
        // The SECURITY DEFINER trigger can audit writes by the restricted SYSTEM
        // role without granting audit-table writes to application connections.
        owner.execute("""
                CREATE TABLE public.c5_test_subscription_update_audit (
                    business_id UUID NOT NULL
                )
                """);
        owner.execute("""
                CREATE FUNCTION public.c5_test_subscription_update_audit_fn()
                RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
                SET search_path = pg_catalog, public
                AS $fn$
                BEGIN
                    INSERT INTO public.c5_test_subscription_update_audit(business_id)
                    VALUES (NEW.business_id);
                    RETURN NEW;
                END;
                $fn$
                """);
        owner.execute("""
                CREATE TRIGGER c5_test_subscription_update_audit
                AFTER UPDATE ON public.business_subscription
                FOR EACH ROW EXECUTE FUNCTION public.c5_test_subscription_update_audit_fn()
                """);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var invoice = new SubscriptionPaymentGateway.RemoteInvoice(
                    invoiceId, subscriptionId(tenantA), "processed", "approved", "paid",
                    OffsetDateTime.parse("2026-10-13T19:00:00Z"));
            CountDownLatch enteredProvider = new CountDownLatch(2);
            when(provider.getInvoice(invoiceId)).thenAnswer(invocation -> {
                enteredProvider.countDown();
                if (!enteredProvider.await(15, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both webhook deliveries must overlap");
                }
                return invoice;
            });
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Void> delivery = () -> {
                assertTrue(start.await(15, TimeUnit.SECONDS));
                scope.runAsSystem(() -> billing.reconcileAuthorizedPayment(invoiceId));
                return null;
            };
            Future<Void> first = workers.submit(delivery);
            Future<Void> second = workers.submit(delivery);
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertEquals(0L, enteredProvider.getCount(), "Both provider deliveries were invoked");
            assertEquals(1L, owner.queryForObject("""
                    SELECT COUNT(*) FROM public.c5_test_subscription_update_audit
                    WHERE business_id = ?
                    """, Long.class, tenantA),
                    "Only one committed database UPDATE is allowed for duplicate delivery");
            assertEquals(0L, owner.queryForObject("""
                    SELECT COUNT(*) FROM public.c5_test_subscription_update_audit
                    WHERE business_id = ?
                    """, Long.class, tenantB),
                    "A duplicated payment must never change another tenant");
            Map<String, Object> a = stored(tenantA);
            assertEquals("PRO", a.get("plan_code"));
            assertEquals("ACTIVE", a.get("status"));
            assertEquals(invoiceId, a.get("last_billing_invoice_id"));
            assertEquals("approved", a.get("last_billing_payment_status"));
            assertEquals("TRIALING", stored(tenantB).get("status"));
            verify(provider, times(2)).getInvoice(invoiceId);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            owner.execute("DROP TRIGGER IF EXISTS c5_test_subscription_update_audit ON public.business_subscription");
            owner.execute("DROP FUNCTION IF EXISTS public.c5_test_subscription_update_audit_fn()");
            owner.execute("DROP TABLE IF EXISTS public.c5_test_subscription_update_audit");
        }
    }

    private static String signTestWebhook(String invoiceId, String requestId, long timestamp, String secret) {
        try {
            // Same HMAC input contract as the official Mercado Pago Java SDK.
            String manifest = "id:" + invoiceId + ";request-id:" + requestId
                    + ";ts:" + timestamp + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                    mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new AssertionError("Test JVM must support HmacSHA256", error);
        }
    }

    @Test
    void signedSdkValidatedWebhookReconcilesRealPostgresAndRejectsForgedOrExpiredSignatures() {
        String invoiceId = "test-signed-invoice-a";
        String requestId = "test-request-a";
        String secret = "synthetic-test-only-hmac-secret-unique";
        long timestamp = Instant.now().getEpochSecond();
        var props = new MercadoPagoProperties();
        props.setEnabled(true);
        props.setAccessToken("TEST-only-test-token");
        props.setWebhookSecret(secret);
        props.setWebhookToleranceSeconds(300);
        var controller = new MercadoPagoWebhookController(props, billing);
        when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                invoiceId, subscriptionId(tenantA), "processed", "approved", "paid",
                OffsetDateTime.parse("2026-10-13T19:00:00Z")));
        String body = "{\"type\":\"subscription_authorized_payment\","
                + "\"data\":{\"id\":\"" + invoiceId + "\"}}";
        String good = signTestWebhook(invoiceId, requestId, timestamp, secret);

        // Invalid HMAC must not reconcile or change another business.
        assertEquals(401, scope.callAsSystem(() -> controller.webhook(
                good.replace(",v1=", ",v1=00"), requestId,
                "subscription_authorized_payment", null, invoiceId, body)).getStatusCode().value());
        assertEquals("TRIALING", stored(tenantA).get("status"));
        assertEquals("TRIALING", stored(tenantB).get("status"));
        verify(provider, never()).getInvoice(invoiceId);

        // Correct HMAC is verified by the real SDK and routed through the real
        // Spring billing service into PostgreSQL.
        assertEquals(204, scope.callAsSystem(() -> controller.webhook(
                good, requestId, "subscription_authorized_payment",
                null, invoiceId, body)).getStatusCode().value());
        Map<String, Object> settled = stored(tenantA);
        assertEquals("ACTIVE", settled.get("status"));
        assertEquals("PRO", settled.get("plan_code"));
        assertEquals(invoiceId, settled.get("last_billing_invoice_id"));
        assertEquals("approved", settled.get("last_billing_payment_status"));
        assertEquals("TRIALING", stored(tenantB).get("status"));

        assertEquals(204, scope.callAsSystem(() -> controller.webhook(
                good, requestId, "subscription_authorized_payment",
                null, invoiceId, body)).getStatusCode().value());
        assertEquals(settled, stored(tenantA), "Duplicate signed webhook must be idempotent");
        assertEquals("TRIALING", stored(tenantB).get("status"));

        // Even a mathematically valid signature with an expired timestamp is
        // rejected without accessing the payment provider or mutating storage.
        String expired = signTestWebhook(invoiceId, requestId, timestamp - 3600, secret);
        assertEquals(401, scope.callAsSystem(() -> controller.webhook(
                expired, requestId, "subscription_authorized_payment",
                null, invoiceId, body)).getStatusCode().value());
        assertEquals(settled, stored(tenantA));
        verify(provider, times(2)).getInvoice(invoiceId);
    }

    @Test
    void signedHttpPostTraversesSpringSecurityAndPersistsExactlyOnceInPostgres() throws Exception {
        String invoiceId = "test-http-invoice-a";
        String requestId = "test-http-request";
        String secret = "synthetic-http-test-only-secret";
        String oldToken = webhookProperties.getAccessToken();
        String oldSecret = webhookProperties.getWebhookSecret();
        boolean oldEnabled = webhookProperties.isEnabled();
        webhookProperties.setEnabled(true);
        webhookProperties.setAccessToken("TEST-only-http-token");
        webhookProperties.setWebhookSecret(secret);
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
            when(provider.getInvoice(invoiceId)).thenReturn(new SubscriptionPaymentGateway.RemoteInvoice(
                    invoiceId, subscriptionId(tenantA), "processed", "approved", "paid",
                    OffsetDateTime.parse("2026-10-13T19:00:00Z")));
            String body = "{\"type\":\"subscription_authorized_payment\","
                    + "\"data\":{\"id\":\"" + invoiceId + "\"}}";
            String signature = signTestWebhook(
                    invoiceId, requestId, Instant.now().getEpochSecond(), secret);

            // Security filters and the actual HTTP parameter binding must allow
            // only a correctly authenticated provider event through to billing.
            assertEquals(401, mvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/webhooks/v1/mercadopago")
                            .param("type", "subscription_authorized_payment")
                            .param("data.id", invoiceId)
                            .header("x-request-id", requestId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn().getResponse().getStatus());
            assertEquals("TRIALING", stored(tenantA).get("status"));
            verify(provider, never()).getInvoice(invoiceId);

            var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/webhooks/v1/mercadopago")
                    .param("type", "subscription_authorized_payment")
                    .param("data.id", invoiceId)
                    .header("x-request-id", requestId)
                    .header("x-signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);

            assertEquals(204, mvc.perform(post).andReturn().getResponse().getStatus());
            Map<String, Object> first = stored(tenantA);
            assertEquals("ACTIVE", first.get("status"));
            assertEquals("PRO", first.get("plan_code"));
            assertEquals(invoiceId, first.get("last_billing_invoice_id"));
            assertEquals("TRIALING", stored(tenantB).get("status"));

            assertEquals(204, mvc.perform(post).andReturn().getResponse().getStatus());
            assertEquals(first, stored(tenantA), "Repeated signed POST must be idempotent");
            assertEquals("TRIALING", stored(tenantB).get("status"));
            verify(provider, times(2)).getInvoice(invoiceId);
        } finally {
            webhookProperties.setWebhookSecret(oldSecret);
            webhookProperties.setAccessToken(oldToken);
            webhookProperties.setEnabled(oldEnabled);
        }
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
