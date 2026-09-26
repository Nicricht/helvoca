package cl.helvoca.payment;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MerchantPaymentStatusReconciliationWorker {
    private static final Logger log =
            LoggerFactory.getLogger(MerchantPaymentStatusReconciliationWorker.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final PaymentWorkflowService payments;

    public MerchantPaymentStatusReconciliationWorker(
            @Value("${HELVOCA_MERCHANT_PAYMENT_RECONCILE_ENABLED:false}") boolean enabled,
            @Value("${HELVOCA_MERCHANT_PAYMENT_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            PaymentWorkflowService payments) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.payments = payments;
    }

    @Scheduled(
            initialDelayString = "${HELVOCA_MERCHANT_PAYMENT_RECONCILE_INITIAL_DELAY_MS:15000}",
            fixedDelayString = "${HELVOCA_MERCHANT_PAYMENT_RECONCILE_DELAY_MS:60000}")
    public void poll() {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.error("MERCHANT_PAYMENT_RECONCILE_SKIPPED reason=invalid_business_id");
            return;
        }

        databaseContext.runAsTenant(tenantId, () -> {
            try {
                int changed = payments.reconcilePending(tenantId);
                if (changed > 0) {
                    log.info("MERCHANT_PAYMENT_RECONCILED businessId={} changed={}", tenantId, changed);
                }
            } catch (Exception e) {
                log.warn("MERCHANT_PAYMENT_RECONCILE_FAILED businessId={} error={}",
                        tenantId, e.getClass().getSimpleName());
            }
        });
    }
}
