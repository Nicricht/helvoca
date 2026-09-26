package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentOperationsAdminService {
    private final BusinessOperationRepository operations;
    private final BusinessPaymentRepository payments;
    private final PaymentWorkflowService workflow;
    private final TenantProvider tenantProvider;

    public PaymentOperationsAdminService(BusinessOperationRepository operations,
                                         BusinessPaymentRepository payments,
                                         PaymentWorkflowService workflow,
                                         TenantProvider tenantProvider) {
        this.operations = operations;
        this.payments = payments;
        this.workflow = workflow;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Diagnostic diagnose(UUID paymentOperationId) {
        return diagnose(tenantProvider.requireBusinessId(), paymentOperationId);
    }

    @Transactional
    public Diagnostic reconcile(UUID paymentOperationId) {
        UUID businessId = tenantProvider.requireBusinessId();
        BusinessPayment payment = payments
                .findByOperationIdAndBusinessId(paymentOperationId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment projection not found"));

        BusinessPayment reconciled = workflow.reconcileOne(businessId, payment.getId());
        if (reconciled == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment projection not found");
        }
        return diagnose(businessId, paymentOperationId);
    }

    Diagnostic diagnose(UUID businessId, UUID paymentOperationId) {
        BusinessOperation paymentOperation = operations
                .findByIdAndBusinessId(paymentOperationId, businessId)
                .filter(op -> op.getType() == BusinessOperation.Type.PAYMENT)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment operation not found"));

        BusinessPayment payment = payments
                .findByOperationIdAndBusinessId(paymentOperationId, businessId)
                .orElse(null);

        UUID orderOperationId = payment != null
                ? payment.getTargetOperationId()
                : metadataUuid(paymentOperation.getMetadata(), "targetOperationId");
        BusinessOperation order = orderOperationId == null
                ? null
                : operations.findByIdAndBusinessId(orderOperationId, businessId)
                        .filter(op -> op.getType() == BusinessOperation.Type.ORDER)
                        .orElse(null);

        UUID journeyOperationId = metadataUuid(
                paymentOperation.getMetadata(), "commercialJourneyOperationId");
        BusinessOperation journey = journeyOperationId == null
                ? null
                : operations.findByIdAndBusinessId(journeyOperationId, businessId).orElse(null);

        UUID journeyOrderOperationId = journey == null
                ? null
                : metadataUuid(journey.getMetadata(), "orderOperationId");
        UUID journeyPaymentOperationId = journey == null
                ? null
                : metadataUuid(journey.getMetadata(), "paymentOperationId");

        long paymentProjectionsForOperation =
                payments.countByBusinessIdAndOperationId(businessId, paymentOperationId);
        long paymentsForTargetOrder = orderOperationId == null
                ? 0
                : payments.countByBusinessIdAndTargetOperationId(businessId, orderOperationId);

        boolean sameCustomer = sameCustomer(paymentOperation, order)
                && sameCustomer(paymentOperation, journey)
                && (payment == null
                    || payment.getCustomerId() == null
                    || paymentOperation.getCustomerId() == null
                    || payment.getCustomerId().equals(paymentOperation.getCustomerId()));

        boolean linkageConsistent = order != null
                && journey != null
                && Objects.equals(orderOperationId, journeyOrderOperationId)
                && Objects.equals(paymentOperationId, journeyPaymentOperationId)
                && sameCustomer
                && paymentProjectionsForOperation <= 1;

        String paymentStatus = payment == null || payment.getStatus() == null
                ? null
                : payment.getStatus().name();
        String commercialStage = metadataString(
                journey == null ? null : journey.getMetadata(), "commercialStage");
        String expectedStage = expectedStage(payment == null ? null : payment.getStatus());
        boolean journeyStateConsistent = payment == null
                || expectedStage == null
                || Objects.equals(expectedStage, commercialStage);

        boolean terminal = payment != null && switch (payment.getStatus()) {
            case SUCCEEDED, FAILED, CANCELLED, EXPIRED, REFUNDED -> true;
            case REQUIRES_ACTION, PENDING -> false;
        };

        String recommendedAction;
        if (payment == null) {
            recommendedAction = "CREATE_PAYMENT";
        } else if (!linkageConsistent) {
            recommendedAction = "REVIEW_LINKAGE";
        } else if (!journeyStateConsistent
                || payment.getStatus() == BusinessPayment.Status.REQUIRES_ACTION
                || payment.getStatus() == BusinessPayment.Status.PENDING) {
            recommendedAction = "RECONCILE";
        } else {
            recommendedAction = "NONE";
        }

        return new Diagnostic(
                businessId,
                journeyOperationId,
                orderOperationId,
                paymentOperationId,
                payment == null ? null : payment.getId(),
                journey == null || journey.getStatus() == null ? null : journey.getStatus().name(),
                order == null || order.getStatus() == null ? null : order.getStatus().name(),
                paymentOperation.getStatus() == null ? null : paymentOperation.getStatus().name(),
                paymentStatus,
                commercialStage,
                payment == null ? null : payment.getProvider(),
                payment == null ? null : payment.getExternalId(),
                payment == null ? null : payment.getCheckoutUrl(),
                metadataString(payment == null ? null : payment.getMetadata(), "remoteStatus"),
                metadataString(payment == null ? null : payment.getMetadata(), "remoteStatusDetail"),
                paymentProjectionsForOperation,
                paymentsForTargetOrder,
                linkageConsistent,
                journeyStateConsistent,
                terminal,
                recommendedAction);
    }

    private static String expectedStage(BusinessPayment.Status status) {
        if (status == null) return null;
        return switch (status) {
            case SUCCEEDED -> "PAID";
            case REQUIRES_ACTION, PENDING -> "PAYMENT_LINK_SENT";
            case REFUNDED -> "PAYMENT_REFUNDED";
            case FAILED, CANCELLED, EXPIRED -> "PAYMENT_FAILED";
        };
    }

    private static boolean sameCustomer(BusinessOperation left, BusinessOperation right) {
        if (left == null || right == null) return false;
        if (left.getCustomerId() == null || right.getCustomerId() == null) return true;
        return left.getCustomerId().equals(right.getCustomerId());
    }

    private static UUID metadataUuid(Map<String, Object> metadata, String key) {
        String raw = metadataString(metadata, key);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) return null;
        Object value = metadata.get(key);
        if (value == null) return null;
        String raw = String.valueOf(value).trim();
        return raw.isBlank() ? null : raw;
    }

    public record Diagnostic(
            UUID businessId,
            UUID journeyOperationId,
            UUID orderOperationId,
            UUID paymentOperationId,
            UUID paymentId,
            String journeyStatus,
            String orderStatus,
            String paymentOperationStatus,
            String paymentStatus,
            String commercialStage,
            String provider,
            String externalId,
            String checkoutUrl,
            String remoteStatus,
            String remoteStatusDetail,
            long paymentProjectionsForOperation,
            long paymentsForTargetOrder,
            boolean linkageConsistent,
            boolean journeyStateConsistent,
            boolean terminal,
            String recommendedAction) {}
}
