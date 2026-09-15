package cl.helvoca.operations;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_automation_policy")
@IdClass(BusinessAutomationPolicy.Key.class)
public class BusinessAutomationPolicy {
    public enum CustomerConfirmation { NONE, EXPLICIT }
    public enum PaymentRequirement { NONE, REQUIRED_AFTER_CONFIRMATION }
    public enum RetryPolicy { NONE, SAFE_AUTOMATIC }
    public enum EscalationPolicy { NEVER, ONLY_IF_UNRESOLVABLE }

    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private BusinessOperation.Type operationType;

    @Column(name = "auto_execute", nullable = false)
    private boolean autoExecute = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_confirmation", nullable = false, length = 20)
    private CustomerConfirmation customerConfirmation = CustomerConfirmation.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_requirement", nullable = false, length = 30)
    private PaymentRequirement paymentRequirement = PaymentRequirement.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_policy", nullable = false, length = 30)
    private RetryPolicy retryPolicy = RetryPolicy.SAFE_AUTOMATIC;

    @Column(name = "max_auto_retries", nullable = false)
    private int maxAutoRetries = 2;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_policy", nullable = false, length = 40)
    private EscalationPolicy escalationPolicy = EscalationPolicy.ONLY_IF_UNRESOLVABLE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public BusinessOperation.Type getOperationType() { return operationType; }
    public void setOperationType(BusinessOperation.Type operationType) { this.operationType = operationType; }
    public boolean isAutoExecute() { return autoExecute; }
    public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }
    public CustomerConfirmation getCustomerConfirmation() { return customerConfirmation; }
    public void setCustomerConfirmation(CustomerConfirmation customerConfirmation) { this.customerConfirmation = customerConfirmation; }
    public PaymentRequirement getPaymentRequirement() { return paymentRequirement; }
    public void setPaymentRequirement(PaymentRequirement paymentRequirement) { this.paymentRequirement = paymentRequirement; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }
    public int getMaxAutoRetries() { return maxAutoRetries; }
    public void setMaxAutoRetries(int maxAutoRetries) { this.maxAutoRetries = maxAutoRetries; }
    public EscalationPolicy getEscalationPolicy() { return escalationPolicy; }
    public void setEscalationPolicy(EscalationPolicy escalationPolicy) { this.escalationPolicy = escalationPolicy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static final class Key implements java.io.Serializable {
        private UUID businessId;
        private BusinessOperation.Type operationType;

        public Key() {}
        public Key(UUID businessId, BusinessOperation.Type operationType) {
            this.businessId = businessId;
            this.operationType = operationType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return java.util.Objects.equals(businessId, key.businessId)
                    && operationType == key.operationType;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(businessId, operationType);
        }
    }
}
