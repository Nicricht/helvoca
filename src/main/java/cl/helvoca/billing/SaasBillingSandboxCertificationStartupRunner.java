package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class SaasBillingSandboxCertificationStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(SaasBillingSandboxCertificationStartupRunner.class);
    private static final Set<String> ALLOWED_PLANS = Set.of("EMPRENDE", "NEGOCIO");

    private final boolean enabled;
    private final boolean sandboxConfirmed;
    private final String businessId;
    private final String payerEmail;
    private final String planCode;
    private final TenantDatabaseContext databaseContext;
    private final BillingSubscriptionService billing;

    public SaasBillingSandboxCertificationStartupRunner(
            @Value("${HELVOCA_SAAS_BILLING_SANDBOX_CERTIFY_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_SAAS_BILLING_SANDBOX_CONFIRMED:false}") boolean sandboxConfirmed,
            @Value("${HELVOCA_SAAS_BILLING_SANDBOX_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_SAAS_BILLING_SANDBOX_PAYER_EMAIL:}") String payerEmail,
            @Value("${HELVOCA_SAAS_BILLING_SANDBOX_PLAN:EMPRENDE}") String planCode,
            TenantDatabaseContext databaseContext,
            BillingSubscriptionService billing) {
        this.enabled = enabled;
        this.sandboxConfirmed = sandboxConfirmed;
        this.businessId = trim(businessId);
        this.payerEmail = trim(payerEmail);
        this.planCode = normalizePlan(planCode);
        this.databaseContext = databaseContext;
        this.billing = billing;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        requireSandboxConfirmed();

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_SAAS_BILLING_SANDBOX_BUSINESS_ID must be a valid UUID", e);
        }

        String validatedEmail = requireTestPayer(payerEmail);
        String validatedPlan = requireAllowedPlan(planCode);

        databaseContext.runAsTenant(tenantId, () -> {
            CertificationResult result = certify(tenantId, validatedEmail, validatedPlan);
            log.info(
                    "SAAS_BILLING_SANDBOX_CHECKOUT_CERTIFIED businessId={} plan={} monthlyPriceClp={} subscriptionId={} reused={} checkoutUrl={}",
                    tenantId,
                    result.planCode(),
                    result.monthlyPriceClp(),
                    result.subscriptionId(),
                    result.reused(),
                    result.checkoutUrl());
        });
    }

    CertificationResult certify(UUID businessId, String payerEmail, String planCode) {
        requireSandboxConfirmed();
        if (businessId == null) throw new IllegalArgumentException("businessId is required");

        String validatedEmail = requireTestPayer(payerEmail);
        String validatedPlan = requireAllowedPlan(planCode);
        BillingSubscriptionService.CheckoutResponse response =
                billing.createCheckout(businessId, validatedEmail, validatedPlan);

        if (response == null
                || response.subscriptionId() == null
                || response.subscriptionId().isBlank()) {
            throw new IllegalStateException(
                    "Mercado Pago SaaS sandbox certification returned no subscription id");
        }
        if (!validatedPlan.equalsIgnoreCase(response.planCode())) {
            throw new IllegalStateException(
                    "Mercado Pago SaaS sandbox certification plan mismatch");
        }
        if (response.monthlyPriceClp() <= 0) {
            throw new IllegalStateException(
                    "Mercado Pago SaaS sandbox certification returned invalid price");
        }

        String checkoutUrl = requireHttpsCheckout(response.checkoutUrl());
        return new CertificationResult(
                response.subscriptionId().trim(),
                checkoutUrl,
                response.planCode().trim().toUpperCase(Locale.ROOT),
                response.monthlyPriceClp(),
                response.reused());
    }

    private void requireSandboxConfirmed() {
        if (!sandboxConfirmed) {
            throw new IllegalStateException(
                    "HELVOCA_SAAS_BILLING_SANDBOX_CONFIRMED must be true before provider certification");
        }
    }

    private static String requireTestPayer(String raw) {
        String value = trim(raw).toLowerCase(Locale.ROOT);
        if (value.isBlank() || !value.endsWith("@testuser.com")) {
            throw new IllegalStateException(
                    "HELVOCA_SAAS_BILLING_SANDBOX_PAYER_EMAIL must belong to a Mercado Pago testuser.com account");
        }
        return value;
    }

    private static String requireAllowedPlan(String raw) {
        String value = normalizePlan(raw);
        if (!ALLOWED_PLANS.contains(value)) {
            throw new IllegalStateException(
                    "HELVOCA_SAAS_BILLING_SANDBOX_PLAN must be EMPRENDE or NEGOCIO");
        }
        return value;
    }

    private static String requireHttpsCheckout(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "Mercado Pago SaaS sandbox certification returned no HTTPS checkout URL");
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Mercado Pago SaaS sandbox certification returned an invalid HTTPS checkout URL", e);
        }
    }

    private static String normalizePlan(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    record CertificationResult(
            String subscriptionId,
            String checkoutUrl,
            String planCode,
            int monthlyPriceClp,
            boolean reused) {}
}
