package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationPolicyServiceTest {

    private final OperationPolicyService policies = new OperationPolicyService();

    @Test
    void transactionalOperationsRequireCustomerConfirmationButNotHumanApproval() {
        for (BusinessOperation.Type type : new BusinessOperation.Type[]{
                BusinessOperation.Type.ORDER,
                BusinessOperation.Type.DELIVERY,
                BusinessOperation.Type.BOOKING,
                BusinessOperation.Type.PAYMENT}) {
            OperationPolicyService.Policy policy = policies.resolve(type);
            assertTrue(policy.autoExecute());
            assertTrue(policy.requiresExplicitConfirmation());
            assertEquals(OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                    policy.escalationPolicy());
            assertFalse(policy.shouldEscalate(OperationPolicyService.FailureClass.TRANSIENT));
            assertFalse(policy.shouldEscalate(OperationPolicyService.FailureClass.RESOLVABLE_WITH_FALLBACK));
            assertTrue(policy.shouldEscalate(OperationPolicyService.FailureClass.UNRESOLVABLE));
        }
    }

    @Test
    void quoteLeadAndRequestExecuteWithoutRoutineConfirmationOrHumanApproval() {
        for (BusinessOperation.Type type : new BusinessOperation.Type[]{
                BusinessOperation.Type.QUOTE,
                BusinessOperation.Type.LEAD,
                BusinessOperation.Type.REQUEST}) {
            OperationPolicyService.Policy policy = policies.resolve(type);
            assertTrue(policy.autoExecute());
            assertFalse(policy.requiresExplicitConfirmation());
            assertTrue(policy.retriesAutomatically());
            assertEquals(2, policy.maxAutoRetries());
            assertEquals(OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                    policy.escalationPolicy());
        }
    }

    @Test
    void paymentNeverRecursivelyRequiresAnotherPayment() {
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.PAYMENT);
        assertFalse(policy.requiresPaymentAfterConfirmation());
    }
}
