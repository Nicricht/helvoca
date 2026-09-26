package cl.helvoca.agent;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CommercialSandboxOrderReadyStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(CommercialSandboxOrderReadyStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String runId;
    private final String amountClp;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final CommercialSandboxE2eCertificationStartupRunner certification;

    public CommercialSandboxOrderReadyStartupRunner(
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_RUN_ID:}") String runId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_AMOUNT_CLP:1000}") String amountClp,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            CommercialSandboxE2eCertificationStartupRunner certification) {
        this.enabled = enabled;
        this.businessId = safe(businessId);
        this.runId = safe(runId);
        this.amountClp = safe(amountClp);
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.certification = certification;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId = parseTenant(businessId);
        String certificationRunId = requireRunId(runId);
        BigDecimal amount = parseAmount(amountClp);

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            CommercialSandboxE2eCertificationStartupRunner.Seed seed =
                    tx.execute(status -> certification.prepareSeed(
                            tenantId, certificationRunId, amount));
            if (seed == null) {
                throw new IllegalStateException(
                        "Commercial sandbox order-ready seed returned no result");
            }

            CommercialSandboxE2eCertificationStartupRunner.OrderReadyResult result =
                    certification.prepareOrderReady(seed);

            log.info(
                    "COMMERCIAL_SANDBOX_ORDER_READY businessId={} runId={} journeyOperationId={} orderOperationId={} orderStatus={} paymentOperationId={} paymentOperationStatus={} paymentProjectionExists={}",
                    tenantId,
                    certificationRunId,
                    result.journeyOperationId(),
                    result.orderOperationId(),
                    result.orderStatus(),
                    result.paymentOperationId(),
                    result.paymentOperationStatus(),
                    result.paymentProjectionExists());
        });
    }

    private static UUID parseTenant(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_BUSINESS_ID must be a valid UUID", e);
        }
    }

    private static String requireRunId(String raw) {
        if (!raw.matches("[A-Za-z0-9_-]{3,80}")) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_RUN_ID must match [A-Za-z0-9_-]{3,80}");
        }
        return raw;
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ONE) < 0
                    || amount.compareTo(new BigDecimal("100000")) > 0
                    || amount.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException();
            }
            return amount;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA_COMMERCIAL_SANDBOX_ORDER_READY_AMOUNT_CLP must be an integer between 1 and 100000", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
