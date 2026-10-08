package cl.helvoca.request;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static cl.helvoca.request.RequestResolutionDecisionEngine.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestResolutionDecisionEngineTest {
    private final RequestResolutionDecisionEngine engine = new RequestResolutionDecisionEngine();

    private static Evidence verified(WorkKind kind) {
        return new Evidence(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                kind, Execution.VERIFIED_SUCCESS, CustomerReceipt.CONFIRMED,
                false, false, true);
    }

    private static void expects(Decision result, Action action, Reason reason) {
        assertEquals(action, result.action());
        assertEquals(reason, result.reason());
    }

    @Test
    void resolvesInformationOnlyWhenExecutionAndCustomerReceiptAreVerified() {
        expects(engine.decide(verified(WorkKind.INFORMATION)),
                Action.RESOLVE, Reason.VERIFIED_COMPLETION);
    }

    @Test
    void resolvesConfirmedBookingRequestWithReceiptAndNoAdditionalWork() {
        expects(engine.decide(verified(WorkKind.BOOKING)),
                Action.RESOLVE, Reason.VERIFIED_COMPLETION);
    }

    @Test
    void neverClosesOrdersQuotesLeadsComplaintsPaymentsOrUnknownWorkOnToolSuccess() {
        for (WorkKind kind : new WorkKind[] {
                WorkKind.ORDER, WorkKind.QUOTE, WorkKind.LEAD,
                WorkKind.COMPLAINT, WorkKind.PAYMENT, WorkKind.OTHER
        }) {
            expects(engine.decide(verified(kind)),
                    Action.KEEP_OPEN, Reason.WORK_TYPE_REQUIRES_FOLLOW_UP);
        }
    }

    @Test
    void physicalWorkRemainsOpenEvenAfterSuccessfulBackendAction() {
        Evidence base = verified(WorkKind.BOOKING);
        Evidence withAdditionalWork = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), base.execution(), base.customerReceipt(),
                true, false, true);
        expects(engine.decide(withAdditionalWork),
                Action.KEEP_OPEN, Reason.WORKFLOW_REQUIRES_BUSINESS_ACTION);
    }

    @Test
    void successfulToolCallWithoutConfirmedDeliveryDoesNotCloseRequest() {
        Evidence base = verified(WorkKind.INFORMATION);
        Evidence withoutReceipt = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), base.execution(), CustomerReceipt.UNCONFIRMED,
                false, false, true);
        expects(engine.decide(withoutReceipt),
                Action.KEEP_OPEN, Reason.RESPONSE_NOT_CONFIRMED);
    }

    @Test
    void missingImmutableEventOrTenantOrOperationNeverCloses() {
        Evidence base = verified(WorkKind.INFORMATION);
        for (int missing = 0; missing < 3; missing++) {
            Evidence withoutProof = new Evidence(
                    missing == 0 ? null : base.businessId(),
                    missing == 1 ? null : base.requestOperationId(),
                    missing == 2 ? null : base.evidenceEventId(),
                    base.workKind(), base.execution(), base.customerReceipt(),
                    false, false, true);
            expects(engine.decide(withoutProof),
                    Action.KEEP_OPEN, Reason.MISSING_IMMUTABLE_EVIDENCE);
        }
    }

    @Test
    void transientAndFallbackAndUnknownFailuresRemainOpen() {
        Evidence base = verified(WorkKind.INFORMATION);
        for (Execution result : new Execution[] {
                Execution.RETRYABLE_FAILURE, Execution.FALLBACK_AVAILABLE,
                Execution.UNKNOWN
        }) {
            Evidence failed = new Evidence(
                    base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                    base.workKind(), result, base.customerReceipt(), false, false, true);
            expects(engine.decide(failed),
                    Action.KEEP_OPEN, Reason.EXECUTION_NOT_VERIFIED);
        }
    }

    @Test
    void unresolvableErrorEscalatesOnlyWhenPolicyAllows() {
        Evidence base = verified(WorkKind.INFORMATION);
        Evidence allowed = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), Execution.UNRESOLVABLE,
                CustomerReceipt.UNCONFIRMED, false, false, true);
        Evidence denied = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), Execution.UNRESOLVABLE,
                CustomerReceipt.UNCONFIRMED, false, false, false);
        expects(engine.decide(allowed),
                Action.ESCALATE, Reason.UNRESOLVABLE_OPERATION);
        expects(engine.decide(denied),
                Action.KEEP_OPEN, Reason.UNRESOLVABLE_WITHOUT_ESCALATION);
    }

    @Test
    void explicitHumanRequestIsNotSilentlyResolved() {
        Evidence base = verified(WorkKind.INFORMATION);
        Evidence allowed = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), base.execution(), base.customerReceipt(), false, true, true);
        Evidence denied = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                base.workKind(), base.execution(), base.customerReceipt(), false, true, false);
        expects(engine.decide(allowed),
                Action.ESCALATE, Reason.CUSTOMER_REQUESTED_HUMAN);
        expects(engine.decide(denied),
                Action.KEEP_OPEN, Reason.HUMAN_ESCALATION_UNAVAILABLE);
    }

    @Test
    void nullOrMissingOutcomeFailsClosed() {
        Evidence base = verified(WorkKind.INFORMATION);
        Evidence withoutOutcome = new Evidence(
                base.businessId(), base.requestOperationId(), base.evidenceEventId(),
                null, null, null, false, false, true);
        expects(engine.decide(withoutOutcome),
                Action.KEEP_OPEN, Reason.EXECUTION_NOT_VERIFIED);
        assertThrows(NullPointerException.class, () -> engine.decide(null));
    }
}
