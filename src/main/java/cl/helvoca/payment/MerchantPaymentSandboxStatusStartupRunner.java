package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MerchantPaymentSandboxStatusStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(MerchantPaymentSandboxStatusStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String externalId;
    private final TenantDatabaseContext databaseContext;
    private final PaymentProviderRegistry providers;

    public MerchantPaymentSandboxStatusStartupRunner(
            @Value("${HELVOCA_MERCHANT_PAYMENT_STATUS_CHECK_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_MERCHANT_PAYMENT_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_MERCHANT_PAYMENT_STATUS_EXTERNAL_ID:}") String externalId,
            TenantDatabaseContext databaseContext,
            PaymentProviderRegistry providers) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.externalId = externalId == null ? "" : externalId.trim();
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
        if (!externalId.matches("[A-Za-z0-9_-]{3,180}")) {
            throw new IllegalStateException(
                    "HELVOCA_MERCHANT_PAYMENT_STATUS_EXTERNAL_ID is invalid");
        }

        databaseContext.runAsTenant(tenantId, () -> {
            try {
                StatusCheck result = check(tenantId, externalId);
                log.info(
                        "MERCHANT_PAYMENT_SANDBOX_STATUS_CHECKED businessId={} provider={} externalId={} status={} remoteStatus={} remoteStatusDetail={}",
                        tenantId,
                        result.provider(),
                        result.externalId(),
                        result.status(),
                        safeLogMessage(result.remoteStatus()),
                        safeLogMessage(result.remoteStatusDetail()));
            } catch (Exception e) {
                log.error(
                        "MERCHANT_PAYMENT_SANDBOX_STATUS_CHECK_FAILED businessId={} externalId={} reason={} message={}",
                        tenantId,
                        externalId,
                        e.getClass().getSimpleName(),
                        safeLogMessage(e.getMessage()));
            }
        });
    }

    StatusCheck check(UUID businessId, String externalId) {
        PaymentProviderAdapter provider = providers.byCode(businessId, "mercadopago")
                .orElseThrow(() -> new IllegalStateException(
                        "Mercado Pago sandbox provider is not available for tenant"));
        PaymentProviderAdapter.StatusResult status = provider.getStatus(
                new PaymentProviderAdapter.StatusCommand(
                        businessId,
                        externalId,
                        "helvoca-mp-sandbox-status:" + externalId));
        if (status == null || status.status() == null) {
            throw new IllegalStateException("Mercado Pago sandbox returned no verified status");
        }
        return new StatusCheck(
                provider.providerCode(),
                externalId,
                status.status(),
                metadataString(status.metadata(), "remoteStatus"),
                metadataString(status.metadata(), "remoteStatusDetail"));
    }

    private static String metadataString(java.util.Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) return null;
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static String safeLogMessage(String message) {
        if (message == null || message.isBlank()) return "";
        String normalized = message
                .replace("\\r", " ")
                .replace("\\n", " ")
                .replace("\\t", " ")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    record StatusCheck(String provider,
                       String externalId,
                       BusinessPayment.Status status,
                       String remoteStatus,
                       String remoteStatusDetail) {}
}
