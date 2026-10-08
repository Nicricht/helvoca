package cl.helvoca.request;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventRepository;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Objects;
import java.util.UUID;

/**
 * Read-only, backend-authoritative observer of AI-created requests.
 *
 * This observes the REAL result of voice/WhatsApp create_request, not the
 * model's opinion. Even a valid REQUEST_CREATED event only proves persistence,
 * not customer delivery or resolution. Part 3 will bind verified delivery and
 * post-commit closure; this observer deliberately performs no mutations,
 * handoffs, provider calls or LLM invocations.
 */
@Service
public class RequestToolOutcomeObservationService {
    public record Observation(boolean trustedCreation,
                              RequestResolutionDecisionEngine.Decision decision) {}

    private final BusinessRequestRepository requests;
    private final BusinessOperationRepository operations;
    private final BusinessOperationEventRepository events;
    private final RequestResolutionDecisionEngine decisions;
    private final MeterRegistry metrics;

    public RequestToolOutcomeObservationService(BusinessRequestRepository requests,
                                                BusinessOperationRepository operations,
                                                BusinessOperationEventRepository events,
                                                RequestResolutionDecisionEngine decisions,
                                                MeterRegistry metrics) {
        this.requests = requests;
        this.operations = operations;
        this.events = events;
        this.decisions = decisions;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Observation observeCreatedRequest(UUID businessId,
                                             UUID sourceReferenceId,
                                             RequestSource source,
                                             UUID requestId,
                                             UUID operationId) {
        BusinessOrder.Source channel = switch (source == null ? RequestSource.MANUAL : source) {
            case AI_CALL -> BusinessOrder.Source.VOICE;
            case AI_WHATSAPP -> BusinessOrder.Source.WHATSAPP;
            default -> null;
        };

        // Only real AI voice/WhatsApp requests are in scope.
        if (channel == null || businessId == null || sourceReferenceId == null
                || requestId == null || operationId == null) {
            return keepOpen();
        }

        BusinessRequest request = requests.findByIdAndBusinessId(requestId, businessId).orElse(null);
        if (request == null || !Objects.equals(request.getOperationId(), operationId)
                || request.getSource() != source || request.getStatus() != RequestStatus.OPEN
                || !Objects.equals(request.getBusinessId(), businessId)
                || (source == RequestSource.AI_CALL
                    && !Objects.equals(request.getCallId(), sourceReferenceId))) {
            count(channel, false);
            return keepOpen();
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.REQUEST
                || !Objects.equals(operation.getBusinessId(), businessId)
                || !Objects.equals(operation.getSourceReferenceId(), sourceReferenceId)
                || operation.getSource() != channel
                || !Objects.equals(operation.getCustomerId(), request.getCustomerId())) {
            count(channel, false);
            return keepOpen();
        }

        BusinessOperationEvent event = events
                .findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(
                        businessId, operationId, "REQUEST_CREATED")
                .orElse(null);
        boolean trusted = event != null
                && event.getId() != null
                && Objects.equals(event.getBusinessId(), businessId)
                && Objects.equals(event.getOperationId(), operationId)
                && Objects.equals(event.getSourceReferenceId(), sourceReferenceId)
                && event.getOperationType() == BusinessOperation.Type.REQUEST
                && event.getChannel() == channel
                && event.getActorType() == BusinessOperationEvent.ActorType.AI
                && "REQUEST_CREATED".equals(event.getEventType());

        count(channel, trusted);

        // The event proves that a REQUEST row was created, NOT that a response
        // reached the customer or that their issue was resolved. Fail closed.
        var evidence = new RequestResolutionDecisionEngine.Evidence(
                businessId, operationId, trusted ? event.getId() : null,
                RequestResolutionDecisionEngine.WorkKind.OTHER,
                RequestResolutionDecisionEngine.Execution.UNKNOWN,
                RequestResolutionDecisionEngine.CustomerReceipt.UNCONFIRMED,
                true, false, false);
        return new Observation(trusted, decisions.decide(evidence));
    }

    private Observation keepOpen() {
        var evidence = new RequestResolutionDecisionEngine.Evidence(
                null, null, null,
                RequestResolutionDecisionEngine.WorkKind.OTHER,
                RequestResolutionDecisionEngine.Execution.UNKNOWN,
                RequestResolutionDecisionEngine.CustomerReceipt.UNCONFIRMED,
                true, false, false);
        return new Observation(false, decisions.decide(evidence));
    }

    private void count(BusinessOrder.Source channel, boolean trusted) {
        // Fixed, low-cardinality labels; never expose PII or tenant IDs.
        metrics.counter("helvoca.request_creation_observed",
                "channel", channel.name(), "trusted", Boolean.toString(trusted)).increment();
    }
}
