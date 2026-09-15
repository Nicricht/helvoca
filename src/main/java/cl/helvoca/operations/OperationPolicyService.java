package cl.helvoca.operations;

import org.springframework.stereotype.Service;

/**
 * Central policy authority for universal operations. The LLM may infer intent,
 * but it never decides whether an operation requires confirmation.
 */
@Service
public class OperationPolicyService {
    public enum ConfirmationRequirement { NONE, EXPLICIT }
    public enum HumanReviewRequirement { NEVER, ON_FAILURE, ALWAYS }

    public record Policy(
            ConfirmationRequirement confirmation,
            HumanReviewRequirement humanReview) {
        public boolean requiresExplicitConfirmation() {
            return confirmation == ConfirmationRequirement.EXPLICIT;
        }
    }

    public Policy resolve(BusinessOperation.Type type) {
        if (type == null) throw new IllegalArgumentException("Operation type is required");
        return switch (type) {
            case ORDER, DELIVERY -> new Policy(
                    ConfirmationRequirement.EXPLICIT,
                    HumanReviewRequirement.ON_FAILURE);
            case QUOTE, LEAD, REQUEST -> new Policy(
                    ConfirmationRequirement.NONE,
                    HumanReviewRequirement.ON_FAILURE);
        };
    }
}
