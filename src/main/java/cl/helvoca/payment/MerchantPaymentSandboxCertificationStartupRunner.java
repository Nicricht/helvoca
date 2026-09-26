package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Component
public class MerchantPaymentSandboxCertificationStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(MerchantPaymentSandboxCertificationStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String amountClp;
    private final TenantDatabaseContext databaseContext;
    private final PaymentProviderRegistry providers;

    public MerchantPaymentSandboxCertificationStartupRunner(
            @Value("${HELVOCA_MERCHANT_PAYMENT_CERTIFY_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_MERCHANT_PAYMENT_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_MERCHANT_PAYMENT_CERTIFY_AMOUNT_CLP:1000}") String amountClp,
            TenantDatabaseContext databaseContext,
            PaymentProviderRegistry providers) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.amountClp = amountClp == null ? "" : amountClp.trim();
        this.databaseContext = databaseContext;
        this.providers = providers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_MERCHANT_PAYMENT_BUSINESS_ID must be a valid UUID", e);
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountClp);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_MERCHANT_PAYMENT_CERTIFY_AMOUNT_CLP must be a valid integer CLP amount", e);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalStateException(
                    "HELVOCA_MERCHANT_PAYMENT_CERTIFY_AMOUNT_CLP must be an integer between 1 and 100000");
        }

        databaseContext.runAsTenant(tenantId, () -> {
            CertificationResult result = certify(tenantId, amount);
            log.info(
                    "MERCHANT_PAYMENT_SANDBOX_CERTIFIED businessId={} amount={} currency=CLP provider={} externalId={} status={} checkoutUrl={}",
                    tenantId,
                    amount.toPlainString(),
                    result.provider(),
                    result.externalId(),
                    result.status(),
                    result.checkoutUrl());
        });
    }

    CertificationResult certify(UUID businessId, BigDecimal amount) {
        PaymentProviderAdapter provider = providers.byCode(businessId, "mercadopago")
                .orElseThrow(() -> new IllegalStateException(
                        "Mercado Pago sandbox provider is not available for tenant"));

        UUID paymentOperationId = UUID.randomUUID();
        UUID targetOperationId = UUID.randomUUID();
        PaymentProviderAdapter.CreateResult result = provider.create(
                new PaymentProviderAdapter.CreateCommand(
                        businessId,
                        paymentOperationId,
                        targetOperationId,
                        amount,
                        "CLP",
                        "helvoca-mp-sandbox-cert-" + paymentOperationId,
                        null,
                        Map.of("certification", true)));

        if (result == null || result.externalId() == null || result.externalId().isBlank()) {
            throw new IllegalStateException("Mercado Pago sandbox certification returned no external order id");
        }
        String checkoutUrl = requireHttps(result.checkoutUrl());
        if (result.status() != BusinessPayment.Status.REQUIRES_ACTION
                && result.status() != BusinessPayment.Status.PENDING) {
            throw new IllegalStateException(
                    "Mercado Pago sandbox certification returned unexpected status " + result.status());
        }

        return new CertificationResult(
                provider.providerCode(),
                result.externalId(),
                result.status(),
                checkoutUrl);
    }

    private static String requireHttps(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Mercado Pago sandbox certification returned no checkout URL");
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Mercado Pago sandbox certification returned an invalid checkout URL", e);
        }
    }

    record CertificationResult(String provider,
                               String externalId,
                               BusinessPayment.Status status,
                               String checkoutUrl) {}
}
