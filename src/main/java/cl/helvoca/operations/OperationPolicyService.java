package cl.helvoca.operations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central policy authority for universal operations.
 *
 * The LLM may infer intent, but it never decides transactional safety. Policies
 * are automation-first: Helvoca should execute by itself whenever backend rules
 * allow it, ask the customer for confirmation when the customer is making a
 * transactional decision, retry only operations declared safe to retry, and
 * escalate to a human only when the case is genuinely unresolvable.
 */
@Service
public class OperationPolicyService {
    public enum ConfirmationRequirement { NONE, EXPLICIT }
    public enum PaymentRequirement { NONE, REQUIRED_AFTER_CONFIRMATION }
    public enum RetryPolicy { NONE, SAFE_AUTOMATIC }
    public enum EscalationPolicy { NEVER, ONLY_IF_UNRESOLVABLE }
    public enum FailureClass { TRANSIENT, RESOLVABLE_WITH_FALLBACK, UNRESOLVABLE }

    public record Policy(
            boolean autoExecute,
            ConfirmationRequirement confirmation,
            PaymentRequirement paymentRequirement,
            RetryPolicy retryPolicy,
            int maxAutoRetries,
            EscalationPolicy escalationPolicy,
            boolean tenantOverride) {

        public boolean requiresExplicitConfirmation() {
            return confirmation == ConfirmationRequirement.EXPLICIT;
        }

        public boolean requiresPaymentAfterConfirmation() {
            return paymentRequirement == PaymentRequirement.REQUIRED_AFTER_CONFIRMATION;
        }

        public boolean retriesAutomatically() {
            return retryPolicy == RetryPolicy.SAFE_AUTOMATIC && maxAutoRetries > 0;
        }

        public boolean shouldEscalate(FailureClass failureClass) {
            if (failureClass == null) return false;
            return escalationPolicy == EscalationPolicy.ONLY_IF_UNRESOLVABLE
                    && failureClass == FailureClass.UNRESOLVABLE;
        }
    }

    private final BusinessAutomationPolicyRepository repository;

    /**
     * Kept for focused unit tests and pure-domain callers. Runtime Spring wiring
     * uses the repository constructor below.
     */
    public OperationPolicyService() {
        this.repository = null;
    }

    @Autowired
    public OperationPolicyService(BusinessAutomationPolicyRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolve platform defaults without tenant overrides.
     */
    public Policy resolve(BusinessOperation.Type type) {
        if (type == null) throw new IllegalArgumentException("Operation type is required");
        return defaults(type);
    }

    /**
     * Resolve the effective policy for one tenant. Missing rows intentionally
     * fall back to automation-first platform defaults.
     */
    public Policy resolve(UUID businessId, BusinessOperation.Type type) {
        if (type == null) throw new IllegalArgumentException("Operation type is required");
        if (businessId == null || repository == null) return defaults(type);
        return repository.findByBusinessIdAndOperationType(businessId, type)
                .map(this::fromEntity)
                .orElseGet(() -> defaults(type));
    }

    public boolean allowsAutomaticExecution(UUID businessId, BusinessOperation.Type type) {
        return resolve(businessId, type).autoExecute();
    }

    public void requireAutomaticExecution(UUID businessId, BusinessOperation.Type type) {
        if (!allowsAutomaticExecution(businessId, type)) {
            throw new IllegalStateException("La automatización de " + type + " está deshabilitada por la política del negocio.");
        }
    }

    private Policy defaults(BusinessOperation.Type type) {
        ConfirmationRequirement confirmation = switch (type) {
            case ORDER, DELIVERY, BOOKING, PAYMENT -> ConfirmationRequirement.EXPLICIT;
            case QUOTE, LEAD, REQUEST -> ConfirmationRequirement.NONE;
        };
        return new Policy(
                true,
                confirmation,
                PaymentRequirement.NONE,
                RetryPolicy.SAFE_AUTOMATIC,
                2,
                EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                false);
    }

    private Policy fromEntity(BusinessAutomationPolicy entity) {
        BusinessOperation.Type type = entity.getOperationType();
        ConfirmationRequirement confirmation = ConfirmationRequirement.valueOf(
                entity.getCustomerConfirmation().name());
        PaymentRequirement payment = PaymentRequirement.valueOf(
                entity.getPaymentRequirement().name());
        RetryPolicy retry = RetryPolicy.valueOf(entity.getRetryPolicy().name());
        EscalationPolicy escalation = EscalationPolicy.valueOf(entity.getEscalationPolicy().name());

        // Defense in depth. Database constraints and the admin service already
        // enforce these floors, but runtime resolution never weakens them even if
        // a row was inserted outside the application.
        if (isTransactional(type)) confirmation = ConfirmationRequirement.EXPLICIT;
        if (type == BusinessOperation.Type.PAYMENT) payment = PaymentRequirement.NONE;

        int retries = retry == RetryPolicy.NONE ? 0 : Math.max(1, Math.min(entity.getMaxAutoRetries(), 5));
        return new Policy(
                entity.isAutoExecute(),
                confirmation,
                payment,
                retry,
                retries,
                escalation,
                true);
    }

    public static boolean isTransactional(BusinessOperation.Type type) {
        return type == BusinessOperation.Type.ORDER
                || type == BusinessOperation.Type.DELIVERY
                || type == BusinessOperation.Type.BOOKING
                || type == BusinessOperation.Type.PAYMENT;
    }
}
