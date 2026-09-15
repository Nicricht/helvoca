package cl.helvoca.operations;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AutomationPolicyAdminService {
    private final BusinessAutomationPolicyRepository repository;
    private final OperationPolicyService policies;
    private final TenantProvider tenantProvider;
    private final AuditService audit;

    public AutomationPolicyAdminService(BusinessAutomationPolicyRepository repository,
                                        OperationPolicyService policies,
                                        TenantProvider tenantProvider,
                                        AuditService audit) {
        this.repository = repository;
        this.policies = policies;
        this.tenantProvider = tenantProvider;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<View> current() {
        UUID businessId = tenantProvider.requireBusinessId();
        List<View> result = new ArrayList<>();
        for (BusinessOperation.Type type : BusinessOperation.Type.values()) {
            result.add(view(type, policies.resolve(businessId, type)));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public View current(BusinessOperation.Type type) {
        UUID businessId = tenantProvider.requireBusinessId();
        return view(requireType(type), policies.resolve(businessId, type));
    }

    @Transactional
    public View patch(BusinessOperation.Type type, Update request) {
        UUID businessId = tenantProvider.requireBusinessId();
        type = requireType(type);
        if (request == null) throw new IllegalArgumentException("Automation policy update is required.");

        OperationPolicyService.Policy effective = policies.resolve(businessId, type);
        boolean autoExecute = request.autoExecute() == null ? effective.autoExecute() : request.autoExecute();
        OperationPolicyService.ConfirmationRequirement confirmation = request.customerConfirmation() == null
                ? effective.confirmation() : request.customerConfirmation();
        OperationPolicyService.PaymentRequirement payment = request.paymentRequirement() == null
                ? effective.paymentRequirement() : request.paymentRequirement();
        OperationPolicyService.RetryPolicy retry = request.retryPolicy() == null
                ? effective.retryPolicy() : request.retryPolicy();
        OperationPolicyService.EscalationPolicy escalation = request.escalationPolicy() == null
                ? effective.escalationPolicy() : request.escalationPolicy();
        int maxRetries = request.maxAutoRetries() == null
                ? effective.maxAutoRetries() : request.maxAutoRetries();

        validate(type, confirmation, payment, retry, maxRetries);
        if (retry == OperationPolicyService.RetryPolicy.NONE) maxRetries = 0;

        BusinessAutomationPolicy entity = repository
                .findByBusinessIdAndOperationType(businessId, type)
                .orElseGet(BusinessAutomationPolicy::new);
        entity.setBusinessId(businessId);
        entity.setOperationType(type);
        entity.setAutoExecute(autoExecute);
        entity.setCustomerConfirmation(BusinessAutomationPolicy.CustomerConfirmation.valueOf(confirmation.name()));
        entity.setPaymentRequirement(BusinessAutomationPolicy.PaymentRequirement.valueOf(payment.name()));
        entity.setRetryPolicy(BusinessAutomationPolicy.RetryPolicy.valueOf(retry.name()));
        entity.setMaxAutoRetries(maxRetries);
        entity.setEscalationPolicy(BusinessAutomationPolicy.EscalationPolicy.valueOf(escalation.name()));
        repository.saveAndFlush(entity);

        audit.success(businessId, "AUTOMATION_POLICY_UPDATED_" + type.name(), "AUTOMATION_POLICY", businessId);
        return view(type, policies.resolve(businessId, type));
    }

    @Transactional
    public View reset(BusinessOperation.Type type) {
        UUID businessId = tenantProvider.requireBusinessId();
        type = requireType(type);
        repository.findByBusinessIdAndOperationType(businessId, type).ifPresent(repository::delete);
        repository.flush();
        audit.success(businessId, "AUTOMATION_POLICY_RESET_" + type.name(), "AUTOMATION_POLICY", businessId);
        return view(type, policies.resolve(businessId, type));
    }

    private static void validate(BusinessOperation.Type type,
                                 OperationPolicyService.ConfirmationRequirement confirmation,
                                 OperationPolicyService.PaymentRequirement payment,
                                 OperationPolicyService.RetryPolicy retry,
                                 int maxRetries) {
        if (OperationPolicyService.isTransactional(type)
                && confirmation != OperationPolicyService.ConfirmationRequirement.EXPLICIT) {
            throw new IllegalArgumentException(
                    "ORDER, DELIVERY, BOOKING and PAYMENT require explicit customer confirmation.");
        }
        if (type == BusinessOperation.Type.PAYMENT
                && payment != OperationPolicyService.PaymentRequirement.NONE) {
            throw new IllegalArgumentException("PAYMENT cannot require another PAYMENT operation.");
        }
        if (retry == OperationPolicyService.RetryPolicy.NONE) {
            if (maxRetries != 0) {
                throw new IllegalArgumentException("maxAutoRetries must be 0 when retryPolicy is NONE.");
            }
        } else if (maxRetries < 1 || maxRetries > 5) {
            throw new IllegalArgumentException("maxAutoRetries must be between 1 and 5 for SAFE_AUTOMATIC.");
        }
    }

    private static BusinessOperation.Type requireType(BusinessOperation.Type type) {
        if (type == null) throw new IllegalArgumentException("Operation type is required.");
        return type;
    }

    private static View view(BusinessOperation.Type type, OperationPolicyService.Policy policy) {
        return new View(
                type,
                policy.autoExecute(),
                policy.confirmation(),
                policy.paymentRequirement(),
                policy.retryPolicy(),
                policy.maxAutoRetries(),
                policy.escalationPolicy(),
                policy.tenantOverride());
    }

    public record Update(
            Boolean autoExecute,
            OperationPolicyService.ConfirmationRequirement customerConfirmation,
            OperationPolicyService.PaymentRequirement paymentRequirement,
            OperationPolicyService.RetryPolicy retryPolicy,
            Integer maxAutoRetries,
            OperationPolicyService.EscalationPolicy escalationPolicy) {}

    public record View(
            BusinessOperation.Type operationType,
            boolean autoExecute,
            OperationPolicyService.ConfirmationRequirement customerConfirmation,
            OperationPolicyService.PaymentRequirement paymentRequirement,
            OperationPolicyService.RetryPolicy retryPolicy,
            int maxAutoRetries,
            OperationPolicyService.EscalationPolicy escalationPolicy,
            boolean tenantOverride) {}
}
