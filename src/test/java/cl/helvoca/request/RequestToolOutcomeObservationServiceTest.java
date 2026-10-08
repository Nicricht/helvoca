package cl.helvoca.request;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventRepository;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static cl.helvoca.request.RequestResolutionDecisionEngine.Action;
import static cl.helvoca.request.RequestResolutionDecisionEngine.Reason;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestToolOutcomeObservationServiceTest {
    private final BusinessRequestRepository requests = mock(BusinessRequestRepository.class);
    private final BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
    private final BusinessOperationEventRepository events = mock(BusinessOperationEventRepository.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RequestToolOutcomeObservationService subject = new RequestToolOutcomeObservationService(
            requests, operations, events, new RequestResolutionDecisionEngine(), metrics);

    private record Context(UUID tenant, UUID conversation, UUID requestId,
                           UUID operationId, RequestSource source) {}

    private Context givenTrustedRequest(RequestSource source) {
        Context context = new Context(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), source);

        BusinessRequest request = mock(BusinessRequest.class);
        when(request.getBusinessId()).thenReturn(context.tenant());
        when(request.getOperationId()).thenReturn(context.operationId());
        when(request.getSource()).thenReturn(source);
        when(request.getStatus()).thenReturn(RequestStatus.OPEN);
        if (source == RequestSource.AI_CALL) {
            when(request.getCallId()).thenReturn(context.conversation());
        }
        when(requests.findByIdAndBusinessId(context.requestId(), context.tenant()))
                .thenReturn(Optional.of(request));

        BusinessOperation operation = new BusinessOperation();
        operation.setId(context.operationId());
        operation.setBusinessId(context.tenant());
        operation.setSourceReferenceId(context.conversation());
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setSource(source == RequestSource.AI_CALL
                ? BusinessOrder.Source.VOICE : BusinessOrder.Source.WHATSAPP);
        when(operations.findByIdAndBusinessId(context.operationId(), context.tenant()))
                .thenReturn(Optional.of(operation));

        BusinessOperationEvent event = mock(BusinessOperationEvent.class);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(event.getBusinessId()).thenReturn(context.tenant());
        when(event.getOperationId()).thenReturn(context.operationId());
        when(event.getSourceReferenceId()).thenReturn(context.conversation());
        when(event.getOperationType()).thenReturn(BusinessOperation.Type.REQUEST);
        when(event.getChannel()).thenReturn(operation.getSource());
        when(event.getActorType()).thenReturn(BusinessOperationEvent.ActorType.AI);
        when(event.getEventType()).thenReturn("REQUEST_CREATED");
        when(events.findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(
                context.tenant(), context.operationId(), "REQUEST_CREATED"))
                .thenReturn(Optional.of(event));
        return context;
    }

    private RequestToolOutcomeObservationService.Observation observe(Context context) {
        return subject.observeCreatedRequest(context.tenant(), context.conversation(), context.source(),
                context.requestId(), context.operationId());
    }

    @Test
    void committedVoiceRequestIsObservedButNeverMarkedResolvedBeforeDelivery() {
        Context context = givenTrustedRequest(RequestSource.AI_CALL);
        var result = observe(context);
        assertTrue(result.trustedCreation());
        assertEquals(Action.KEEP_OPEN, result.decision().action());
        assertEquals(Reason.EXECUTION_NOT_VERIFIED, result.decision().reason());
        assertEquals(1.0, metrics.counter("helvoca.request_creation_observed",
                "channel", "VOICE", "trusted", "true").count());
    }

    @Test
    void committedWhatsAppRequestIsObservedWithoutAddingAnyProviderAction() {
        Context context = givenTrustedRequest(RequestSource.AI_WHATSAPP);
        var result = observe(context);
        assertTrue(result.trustedCreation());
        assertEquals(Action.KEEP_OPEN, result.decision().action());
        assertEquals(1.0, metrics.counter("helvoca.request_creation_observed",
                "channel", "WHATSAPP", "trusted", "true").count());
    }

    @Test
    void mismatchedOperationIdFailsClosedWithoutLoadingUnrelatedOperation() {
        Context context = givenTrustedRequest(RequestSource.AI_CALL);
        var result = subject.observeCreatedRequest(context.tenant(), context.conversation(), context.source(),
                context.requestId(), UUID.randomUUID());
        assertFalse(result.trustedCreation());
        assertEquals(Action.KEEP_OPEN, result.decision().action());
        verify(operations, never()).findByIdAndBusinessId(any(), any());
    }

    @Test
    void eventForWrongSourceIsNotTrusted() {
        Context context = givenTrustedRequest(RequestSource.AI_WHATSAPP);
        BusinessOperationEvent mismatch = mock(BusinessOperationEvent.class);
        when(mismatch.getId()).thenReturn(UUID.randomUUID());
        when(mismatch.getBusinessId()).thenReturn(context.tenant());
        when(mismatch.getOperationId()).thenReturn(context.operationId());
        when(mismatch.getSourceReferenceId()).thenReturn(UUID.randomUUID());
        when(events.findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(
                context.tenant(), context.operationId(), "REQUEST_CREATED"))
                .thenReturn(Optional.of(mismatch));
        assertFalse(observe(context).trustedCreation());
    }

    @Test
    void missingImmutableEventIsNotTrusted() {
        Context context = givenTrustedRequest(RequestSource.AI_CALL);
        when(events.findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(
                context.tenant(), context.operationId(), "REQUEST_CREATED"))
                .thenReturn(Optional.empty());
        var result = observe(context);
        assertFalse(result.trustedCreation());
        assertEquals(Action.KEEP_OPEN, result.decision().action());
    }

    @Test
    void crossTenantRequestDoesNotReadForeignDataOrResolve() {
        Context context = givenTrustedRequest(RequestSource.AI_CALL);
        UUID anotherTenant = UUID.randomUUID();
        var result = subject.observeCreatedRequest(
                anotherTenant, context.conversation(), context.source(),
                context.requestId(), context.operationId());
        assertFalse(result.trustedCreation());
        verify(operations, never()).findByIdAndBusinessId(any(), any());
        verify(events, never()).findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(any(), any(), any());
    }

    @Test
    void unsupportedSourcesAndIncompleteContextsFailClosedWithoutDatabaseQueries() {
        Context context = givenTrustedRequest(RequestSource.AI_CALL);
        assertFalse(subject.observeCreatedRequest(context.tenant(), context.conversation(),
                RequestSource.MANUAL, context.requestId(), context.operationId()).trustedCreation());
        assertFalse(subject.observeCreatedRequest(context.tenant(), null,
                RequestSource.AI_CALL, context.requestId(), context.operationId()).trustedCreation());
        verify(requests, never()).findByIdAndBusinessId(any(), any());
    }

    @Test
    void validCreationEventButHumanOriginIsNotCountedAsAi() {
        Context context = givenTrustedRequest(RequestSource.AI_WHATSAPP);
        BusinessOperationEvent human = mock(BusinessOperationEvent.class);
        when(human.getId()).thenReturn(UUID.randomUUID());
        when(human.getBusinessId()).thenReturn(context.tenant());
        when(human.getOperationId()).thenReturn(context.operationId());
        when(human.getSourceReferenceId()).thenReturn(context.conversation());
        when(human.getOperationType()).thenReturn(BusinessOperation.Type.REQUEST);
        when(human.getChannel()).thenReturn(BusinessOrder.Source.WHATSAPP);
        when(human.getActorType()).thenReturn(BusinessOperationEvent.ActorType.HUMAN);
        when(events.findFirstByBusinessIdAndOperationIdAndEventTypeOrderBySequenceNoDesc(
                context.tenant(), context.operationId(), "REQUEST_CREATED"))
                .thenReturn(Optional.of(human));
        assertFalse(observe(context).trustedCreation());
    }
}
