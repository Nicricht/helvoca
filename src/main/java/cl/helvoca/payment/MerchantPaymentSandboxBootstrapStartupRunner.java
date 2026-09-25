package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Component
public class MerchantPaymentSandboxBootstrapStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(MerchantPaymentSandboxBootstrapStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String credentialRef;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final PaymentProviderConfigRepository configs;
    private final PaymentProviderCredentialResolver credentials;

    public MerchantPaymentSandboxBootstrapStartupRunner(
            @Value("${HELVOCA_MERCHANT_PAYMENT_BOOTSTRAP_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_MERCHANT_PAYMENT_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_MERCHANT_PAYMENT_CREDENTIAL_REF:}") String credentialRef,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            PaymentProviderConfigRepository configs,
            PaymentProviderCredentialResolver credentials) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.credentialRef = credentialRef == null ? "" : credentialRef.trim().toUpperCase();
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.configs = configs;
        this.credentials = credentials;
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
        if (!credentialRef.matches("[A-Z0-9_]{2,80}")) {
            throw new IllegalStateException(
                    "HELVOCA_MERCHANT_PAYMENT_CREDENTIAL_REF must match [A-Z0-9_]{2,80}");
        }

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            BootstrapResult result = tx.execute(status -> bootstrap(tenantId));
            if (result == null) {
                throw new IllegalStateException("Merchant payment bootstrap returned no result");
            }
            log.info(
                    "MERCHANT_PAYMENT_SANDBOX_BOOTSTRAPPED businessId={} changed={} provider={} mode={} enabled={} credentialsConfigured={} webhookPath={}",
                    tenantId,
                    result.changed(),
                    result.provider(),
                    result.mode(),
                    result.enabled(),
                    result.credentialsConfigured(),
                    result.webhookPath());
        });
    }

    BootstrapResult bootstrap(UUID businessId) {
        boolean credentialsConfigured = credentials.resolve(credentialRef).isPresent();
        PaymentProviderConfig config = configs.findById(businessId).orElse(null);
        boolean changed = false;
        boolean created = false;

        if (config == null) {
            config = new PaymentProviderConfig();
            config.setBusinessId(businessId);
            config.setEnabled(false);
            changed = true;
            created = true;
        }
        if (!"mercadopago".equalsIgnoreCase(config.getProvider())) {
            config.setProvider("mercadopago");
            changed = true;
        }
        if (config.getMode() != PaymentProviderConfig.Mode.SANDBOX) {
            config.setMode(PaymentProviderConfig.Mode.SANDBOX);
            changed = true;
        }
        if (!credentialRef.equals(config.getCredentialRef())) {
            config.setCredentialRef(credentialRef);
            changed = true;
        }

        if (credentialsConfigured && !config.isEnabled()) {
            config.setEnabled(true);
            changed = true;
        } else if (!credentialsConfigured && created) {
            config.setEnabled(false);
        }

        if (changed) {
            config = configs.saveAndFlush(config);
        }

        String webhookPath = config.getWebhookKey() == null
                ? null
                : "/webhooks/v1/payments/mercadopago/" + config.getWebhookKey();
        return new BootstrapResult(
                changed,
                config.getProvider(),
                config.getMode(),
                config.isEnabled(),
                credentialsConfigured,
                webhookPath);
    }

    record BootstrapResult(boolean changed,
                           String provider,
                           PaymentProviderConfig.Mode mode,
                           boolean enabled,
                           boolean credentialsConfigured,
                           String webhookPath) {}
}
