package cl.helvoca.agent;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class CommercialSandboxExistingPaymentStartupRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(CommercialSandboxExistingPaymentStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String journeyOperationId;
    private final String orderOperationId;
    private final String paymentOperationId;
    private final TenantDatabaseContext databaseContext;
    private final BusinessOperationRepository operations;
    private final BusinessPaymentRepository payments;
    private final CommercialOperationToolService commercial;

    public CommercialSandboxExistingPaymentStartupRunner(
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_EXISTING_PAYMENT_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_EXISTING_PAYMENT_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_EXISTING_PAYMENT_JOURNEY_ID:}") String journeyOperationId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_EXISTING_PAYMENT_ORDER_ID:}") String orderOperationId,
            @Value("${HELVOCA_COMMERCIAL_SANDBOX_EXISTING_PAYMENT_OPERATION_ID:}") String paymentOperationId,
            TenantDatabaseContext databaseContext,
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            CommercialOperationToolService commercial) {
        this.enabled = enabled;
        this.businessId = safe(businessId);
        this.journeyOperationId = safe(journeyOperationId);
        this.orderOperationId = safe(orderOperationId);
        this.paymentOperationId = safe(paymentOperationId);
        this.databaseContext = databaseContext;
        this.operations = operations;
        this.payments = payments;
        this.commercial = commercial;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId = uuid(businessId, "business id");
        UUID journeyId = uuid(journeyOperationId, "journey operation id");
        UUID orderId = uuid(orderOperationId, "order operation id");
        UUID paymentId = uuid(paymentOperationId, "payment operation id");

        databaseContext.runAsTenant(tenantId, () -> {
            try {
                ActivationResult result = activate(tenantId, journeyId, orderId, paymentId);
                log.info(
                        "COMMERCIAL_SANDBOX_EXISTING_PAYMENT_READY businessId={} journeyOperationId={} orderOperationId={} paymentOperationId={} paymentId={} externalId={} paymentStatus={} idempotentReplay={} checkoutUrl={}",
                        tenantId,
                        result.journeyOperationId(),
                        result.orderOperationId(),
                        result.paymentOperationId(),
                        result.paymentId(),
                        result.externalId(),
                        result.paymentStatus(),
                        result.idempotentReplay(),
                        result.checkoutUrl());
            } catch (Exception e) {
                log.error(
                        "COMMERCIAL_SANDBOX_EXISTING_PAYMENT_FAILED businessId={} journeyOperationId={} orderOperationId={} paymentOperationId={} reason={} message={}",
                        tenantId,
                        journeyId,
                        orderId,
                        paymentId,
                        e.getClass().getSimpleName(),
                        e.getMessage() == null ? "" : e.getMessage());
            }
        });
    }

    ActivationResult activate(UUID businessId,
                              UUID journeyId,
                              UUID orderId,
                              UUID paymentOperationId) {
        BusinessOperation journey = requireOperation(
                businessId, journeyId, BusinessOperation.Type.REQUEST, "journey");
        BusinessOperation order = requireOperation(
                businessId, orderId, BusinessOperation.Type.ORDER, "order");
        BusinessOperation paymentOperation = requireOperation(
                businessId, paymentOperationId, BusinessOperation.Type.PAYMENT, "payment");

        if (!sameCustomer(journey, order) || !sameCustomer(journey, paymentOperation)) {
            throw new IllegalStateException("Commercial sandbox payment linkage has different customers");
        }
        if (order.getStatus() != BusinessOperation.Status.CONFIRMED) {
            throw new IllegalStateException("Commercial sandbox order is not CONFIRMED");
        }

        requireMetadataUuid(journey, "orderOperationId", orderId);
        requireMetadataUuid(journey, "paymentOperationId", paymentOperationId);
        requireMetadataUuid(paymentOperation, "targetOperationId", orderId);
        requireMetadataUuid(paymentOperation, "commercialJourneyOperationId", journeyId);

        BusinessPayment existing = payments
                .findByOperationIdAndBusinessId(paymentOperationId, businessId)
                .orElse(null);
        if (existing != null) {
            return result(journeyId, orderId, existing, true);
        }

        if (paymentOperation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION
                || paymentOperation.getConfirmationToken() == null) {
            throw new IllegalStateException(
                    "Commercial sandbox payment is not awaiting confirmation");
        }
        if (paymentOperation.getCustomerId() == null) {
            throw new IllegalStateException(
                    "Commercial sandbox payment has no customer");
        }

        JSONObject response = new JSONObject(commercial.execute(
                businessId,
                paymentOperation.getCustomerId(),
                paymentOperation.getSourceReferenceId(),
                paymentOperation.getContactPhone(),
                paymentOperation.getSource() == null
                        ? BusinessOrder.Source.WHATSAPP
                        : paymentOperation.getSource(),
                "create_payment",
                new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("confirmationToken", paymentOperation.getConfirmationToken().toString())
                        .toString()));

        if (!response.optBoolean("success", false)) {
            JSONObject error = response.optJSONObject("error");
            String code = error == null ? "UNKNOWN" : error.optString("code", "UNKNOWN");
            String message = error == null ? "" : error.optString("message", "");
            throw new IllegalStateException(code + ": " + message);
        }

        BusinessPayment created = payments
                .findByOperationIdAndBusinessId(paymentOperationId, businessId)
                .orElseThrow(() -> new IllegalStateException(
                        "Provider returned success but payment projection was not persisted"));

        return result(journeyId, orderId, created, false);
    }

    private BusinessOperation requireOperation(UUID businessId,
                                               UUID operationId,
                                               BusinessOperation.Type type,
                                               String label) {
        BusinessOperation operation = operations
                .findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalStateException(
                        "Commercial sandbox " + label + " operation was not found"));
        if (operation.getType() != type) {
            throw new IllegalStateException(
                    "Commercial sandbox " + label + " operation has unexpected type");
        }
        return operation;
    }

    private static void requireMetadataUuid(BusinessOperation operation,
                                            String key,
                                            UUID expected) {
        Map<String, Object> metadata = operation.getMetadata();
        Object raw = metadata == null ? null : metadata.get(key);
        UUID actual;
        try {
            actual = raw == null ? null : UUID.fromString(String.valueOf(raw));
        } catch (Exception e) {
            actual = null;
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Commercial sandbox linkage mismatch for " + key);
        }
    }

    private static boolean sameCustomer(BusinessOperation left, BusinessOperation right) {
        if (left.getCustomerId() == null || right.getCustomerId() == null) return false;
        return left.getCustomerId().equals(right.getCustomerId());
    }

    private static ActivationResult result(UUID journeyId,
                                           UUID orderId,
                                           BusinessPayment payment,
                                           boolean replay) {
        if (payment.getExternalId() == null || payment.getExternalId().isBlank()) {
            throw new IllegalStateException("Commercial sandbox payment has no external id");
        }
        if (payment.getCheckoutUrl() == null || payment.getCheckoutUrl().isBlank()) {
            throw new IllegalStateException("Commercial sandbox payment has no checkout URL");
        }
        return new ActivationResult(
                journeyId,
                orderId,
                payment.getOperationId(),
                payment.getId(),
                payment.getExternalId(),
                payment.getStatus(),
                payment.getCheckoutUrl(),
                replay);
    }

    private static UUID uuid(String raw, String label) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HELVOCA commercial sandbox existing payment " + label + " must be a valid UUID", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record ActivationResult(UUID journeyOperationId,
                            UUID orderOperationId,
                            UUID paymentOperationId,
                            UUID paymentId,
                            String externalId,
                            BusinessPayment.Status paymentStatus,
                            String checkoutUrl,
                            boolean idempotentReplay) {}
}
