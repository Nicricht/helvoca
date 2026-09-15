package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationPolicyServiceTest {

    private final OperationPolicyService policies = new OperationPolicyService();

    @Test
    void bookingRequiresExplicitConfirmationByPolicy() {
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.BOOKING);
        assertTrue(policy.requiresExplicitConfirmation());
        assertEquals(OperationPolicyService.HumanReviewRequirement.ON_FAILURE, policy.humanReview());
    }

    @Test
    void paymentRequiresExplicitConfirmationByPolicy() {
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.PAYMENT);
        assertTrue(policy.requiresExplicitConfirmation());
        assertEquals(OperationPolicyService.HumanReviewRequirement.ON_FAILURE, policy.humanReview());
    }

    @Test
    void quoteLeadAndRequestRemainNonTransactionalConfirmationPolicies() {
        assertFalse(policies.resolve(BusinessOperation.Type.QUOTE).requiresExplicitConfirmation());
        assertFalse(policies.resolve(BusinessOperation.Type.LEAD).requiresExplicitConfirmation());
        assertFalse(policies.resolve(BusinessOperation.Type.REQUEST).requiresExplicitConfirmation());
    }
}
