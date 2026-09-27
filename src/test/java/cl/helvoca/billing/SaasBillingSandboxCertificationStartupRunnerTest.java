package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaasBillingSandboxCertificationStartupRunnerTest {

    @Test
    void disabledRunnerNeverCallsBilling() throws Exception {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, false, "", "", "EMPRENDE", billing);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(billing);
    }

    @Test
    void enabledRunnerFailsClosedWithoutExplicitSandboxConfirmation() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        SaasBillingSandboxCertificationStartupRunner runner = runner(
                true, false, UUID.randomUUID().toString(), "test@testuser.com", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("SANDBOX_CONFIRMED"));
        verifyNoInteractions(billing);
    }

    @Test
    void enabledRunnerRejectsInvalidBusinessIdBeforeProviderCall() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        SaasBillingSandboxCertificationStartupRunner runner = runner(
                true, true, "not-a-uuid", "test@testuser.com", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("BUSINESS_ID"));
        verifyNoInteractions(billing);
    }

    @Test
    void rejectsNonTestPayerBeforeProviderCall() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        SaasBillingSandboxCertificationStartupRunner runner = runner(
                true, true, UUID.randomUUID().toString(), "customer@example.com", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("testuser.com"));
        verifyNoInteractions(billing);
    }

    @Test
    void rejectsEnterpriseAndUnknownPlansBeforeProviderCall() {
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        UUID businessId = UUID.randomUUID();

        for (String plan : new String[]{"ENTERPRISE", "UNKNOWN"}) {
            SaasBillingSandboxCertificationStartupRunner runner = runner(
                    true, true, businessId.toString(), "test@testuser.com", plan, billing);

            assertThrows(
                    IllegalStateException.class,
                    () -> runner.run(new DefaultApplicationArguments(new String[0])));
        }

        verifyNoInteractions(billing);
    }

    @Test
    void createsOneSandboxCheckoutForAllowedPlanInsideTenantContext() throws Exception {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);

        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(1);
            work.run();
            return null;
        }).when(databaseContext).runAsTenant(eq(businessId), any(Runnable.class));

        when(billing.createCheckout(businessId, "test@testuser.com", "NEGOCIO"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        "preapproval-test-123",
                        "https://www.mercadopago.cl/subscriptions/checkout?preapproval_id=test",
                        "NEGOCIO",
                        "Negocio",
                        39990,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner =
                new SaasBillingSandboxCertificationStartupRunner(
                        true,
                        true,
                        businessId.toString(),
                        "test@testuser.com",
                        "negocio",
                        databaseContext,
                        billing);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(databaseContext).runAsTenant(eq(businessId), any(Runnable.class));
        verify(billing).createCheckout(businessId, "test@testuser.com", "NEGOCIO");
    }

    @Test
    void rejectsNullProviderResponse() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(null);

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("no subscription id"));
    }

    @Test
    void rejectsProviderResponseWithoutSubscriptionId() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        null,
                        "https://www.mercadopago.cl/subscriptions/checkout?preapproval_id=test",
                        "EMPRENDE",
                        "Emprende",
                        24990,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("no subscription id"));
    }

    @Test
    void rejectsProviderResponseWithInvalidPrice() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        "preapproval-test-123",
                        "https://www.mercadopago.cl/subscriptions/checkout?preapproval_id=test",
                        "EMPRENDE",
                        "Emprende",
                        0,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("invalid price"));
    }

    @Test
    void rejectsProviderResponseWithoutCheckoutUrl() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        "preapproval-test-123",
                        null,
                        "EMPRENDE",
                        "Emprende",
                        24990,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("no HTTPS checkout URL"));
    }

    @Test
    void rejectsProviderResponseWithoutHttpsCheckout() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        "preapproval-test-123",
                        "http://unsafe.example.test/checkout",
                        "EMPRENDE",
                        "Emprende",
                        24990,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("HTTPS checkout"));
    }

    @Test
    void rejectsProviderResponseWithMismatchedPlan() {
        UUID businessId = UUID.randomUUID();
        BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        when(billing.createCheckout(businessId, "test@testuser.com", "EMPRENDE"))
                .thenReturn(new BillingSubscriptionService.CheckoutResponse(
                        "preapproval-test-123",
                        "https://www.mercadopago.cl/subscriptions/checkout?preapproval_id=test",
                        "NEGOCIO",
                        "Negocio",
                        39990,
                        false));

        SaasBillingSandboxCertificationStartupRunner runner = runner(
                false, true, "", "", "EMPRENDE", billing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner.certify(businessId, "test@testuser.com", "EMPRENDE"));

        assertTrue(error.getMessage().contains("plan mismatch"));
    }

    private static SaasBillingSandboxCertificationStartupRunner runner(
            boolean enabled,
            boolean sandboxConfirmed,
            String businessId,
            String payerEmail,
            String planCode,
            BillingSubscriptionService billing) {
        return new SaasBillingSandboxCertificationStartupRunner(
                enabled,
                sandboxConfirmed,
                businessId,
                payerEmail,
                planCode,
                mock(TenantDatabaseContext.class),
                billing);
    }
}
