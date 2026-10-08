package cl.helvoca.request;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure, fail-closed decision point for request automation.
 *
 * This service neither invokes an AI/provider nor changes a request. Callers
 * must load and verify immutable evidence for the authenticated tenant before
 * applying any decision. A tool's success flag alone is never proof that the
 * customer received a response or that physical work was completed.
 */
@Component
public final class RequestResolutionDecisionEngine {
    public enum WorkKind {
        INFORMATION, BOOKING, ORDER, QUOTE, LEAD, COMPLAINT, PAYMENT, OTHER
    }

    public enum Execution {
        VERIFIED_SUCCESS, RETRYABLE_FAILURE, FALLBACK_AVAILABLE, UNRESOLVABLE, UNKNOWN
    }

    public enum CustomerReceipt {
        CONFIRMED, UNCONFIRMED
    }

    public enum Action {
        RESOLVE, KEEP_OPEN, ESCALATE
    }

    public enum Reason {
        CUSTOMER_REQUESTED_HUMAN,
        HUMAN_ESCALATION_UNAVAILABLE,
        UNRESOLVABLE_OPERATION,
        UNRESOLVABLE_WITHOUT_ESCALATION,
        EXECUTION_NOT_VERIFIED,
        WORKFLOW_REQUIRES_BUSINESS_ACTION,
        WORK_TYPE_REQUIRES_FOLLOW_UP,
        MISSING_IMMUTABLE_EVIDENCE,
        RESPONSE_NOT_CONFIRMED,
        VERIFIED_COMPLETION
    }

    /**
     * Evidence IDs are references, not user-provided claims. The integration
     * layer must verify that the event is immutable, belongs to businessId and
     * proves this request's operation and the stated execution outcome.
     *
     * A confirmed customer receipt must be sourced from the channel's actual
     * delivery/acknowledgement mechanism, never a model-generated assertion.
     */
    public record Evidence(
            UUID businessId,
            UUID requestOperationId,
            UUID evidenceEventId,
            WorkKind workKind,
            Execution execution,
            CustomerReceipt customerReceipt,
            boolean additionalBusinessActionRequired,
            boolean customerRequestedHuman,
            boolean escalationPermitted
    ) { }

    public record Decision(Action action, Reason reason) {
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public Decision decide(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");

        // A request to speak with someone cannot be silently marked resolved.
        if (evidence.customerRequestedHuman()) {
            return evidence.escalationPermitted()
                    ? decision(Action.ESCALATE, Reason.CUSTOMER_REQUESTED_HUMAN)
                    : decision(Action.KEEP_OPEN, Reason.HUMAN_ESCALATION_UNAVAILABLE);
        }

        if (evidence.execution() == Execution.UNRESOLVABLE) {
            return evidence.escalationPermitted()
                    ? decision(Action.ESCALATE, Reason.UNRESOLVABLE_OPERATION)
                    : decision(Action.KEEP_OPEN, Reason.UNRESOLVABLE_WITHOUT_ESCALATION);
        }

        // Retryable failures, fallbacks and unknown outcomes must remain open.
        if (evidence.execution() != Execution.VERIFIED_SUCCESS) {
            return decision(Action.KEEP_OPEN, Reason.EXECUTION_NOT_VERIFIED);
        }

        // Physical preparation, delivery and any further human work cannot be
        // inferred from a successful tool call.
        if (evidence.additionalBusinessActionRequired()) {
            return decision(Action.KEEP_OPEN, Reason.WORKFLOW_REQUIRES_BUSINESS_ACTION);
        }

        // A completed conversation does not prove a payment, accepted quote,
        // successful complaint resolution or fulfillment of an order.
        if (evidence.workKind() != WorkKind.INFORMATION
                && evidence.workKind() != WorkKind.BOOKING) {
            return decision(Action.KEEP_OPEN, Reason.WORK_TYPE_REQUIRES_FOLLOW_UP);
        }

        if (evidence.businessId() == null
                || evidence.requestOperationId() == null
                || evidence.evidenceEventId() == null) {
            return decision(Action.KEEP_OPEN, Reason.MISSING_IMMUTABLE_EVIDENCE);
        }

        if (evidence.customerReceipt() != CustomerReceipt.CONFIRMED) {
            return decision(Action.KEEP_OPEN, Reason.RESPONSE_NOT_CONFIRMED);
        }

        return decision(Action.RESOLVE, Reason.VERIFIED_COMPLETION);
    }

    private static Decision decision(Action action, Reason reason) {
        return new Decision(action, reason);
    }
}
