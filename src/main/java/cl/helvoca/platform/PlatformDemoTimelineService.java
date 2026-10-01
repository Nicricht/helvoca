package cl.helvoca.platform;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallSummaryRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationEvent;
import cl.helvoca.operations.BusinessOperationEventRepository;
import cl.helvoca.operations.BusinessOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformDemoTimelineService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository conversations;
    private final BusinessOperationRepository operations;
    private final BusinessOperationEventRepository operationEvents;
    private final CallSummaryRepository summaries;
    private final CallActionRepository actions;

    public PlatformDemoTimelineService(DemoRuntimeProperties properties,
                                       DemoSessionRepository sessions,
                                       CallSessionRepository calls,
                                       MessagingConversationRepository conversations,
                                       BusinessOperationRepository operations,
                                       BusinessOperationEventRepository operationEvents,
                                       CallSummaryRepository summaries,
                                       CallActionRepository actions) {
        this.properties = properties;
        this.sessions = sessions;
        this.calls = calls;
        this.conversations = conversations;
        this.operations = operations;
        this.operationEvents = operationEvents;
        this.summaries = summaries;
        this.actions = actions;
    }

    @Transactional(readOnly = true)
    public PlatformDemoTimelineResponse timeline(UUID sessionId) {
        UUID runtimeId = requireConfiguredRuntime();
        DemoSession session = sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)
                .orElseThrow(() -> new NotFoundException("Demo session not found"));

        List<CallSession> scopedCalls =
                calls.findAllByBusinessIdAndDemoSessionIdOrderByStartedAtAsc(runtimeId, sessionId);
        List<MessagingConversation> scopedConversations =
                conversations.findAllByBusinessIdAndDemoSessionIdOrderByOpenedAtAsc(runtimeId, sessionId);
        List<BusinessOperation> scopedOperations =
                operations.findAllByBusinessIdAndDemoSessionIdOrderByCreatedAtAsc(runtimeId, sessionId);

        List<PlatformDemoTimelineResponse.TimelineEvent> events = new ArrayList<>();
        List<String> facts = new ArrayList<>();

        events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                session.getStartedAt() != null ? session.getStartedAt() : session.getCreatedAt(),
                "SESSION", session.getId(), session.getState().name(),
                "Demo session " + session.getState().name()));

        for (CallSession call : scopedCalls) {
            String resolution = clean(call.getResolution());
            events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                    call.getStartedAt(), "CALL", call.getId(),
                    call.getStatus() == null ? null : call.getStatus().name(),
                    resolution == null ? "Recorded call" : "Recorded call · " + resolution));

            summaries.findByCallId(call.getId()).ifPresent(summary -> {
                String outcome = clean(summary.getOutcome());
                String detail = clean(summary.getSummary());
                events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                        summary.getCreatedAt() != null ? summary.getCreatedAt() : call.getStartedAt(),
                        "CALL_SUMMARY", call.getId(), outcome,
                        detail == null ? "Persisted call summary" : detail));
                if (outcome != null) facts.add("Call outcome: " + outcome);
            });

            for (CallAction action : actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(
                    runtimeId, call.getId())) {
                String status = action.isSuccess() ? "SUCCEEDED" : "FAILED";
                String detail = clean(action.getDetail());
                if (detail == null) detail = clean(action.getErrorCode());
                events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                        action.getCreatedAt(), "CALL_ACTION", action.getId(), status,
                        action.getActionType() + (detail == null ? "" : " · " + detail)));
                if (action.isSuccess() && clean(action.getActionType()) != null) {
                    facts.add("Recorded action: " + action.getActionType());
                }
            }
        }

        for (MessagingConversation conversation : scopedConversations) {
            events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                    conversation.getOpenedAt(), "CONVERSATION", conversation.getId(),
                    clean(conversation.getChannel()), "Recorded messaging conversation"));
        }

        for (BusinessOperation operation : scopedOperations) {
            String type = operation.getType() == null ? "OPERATION" : operation.getType().name();
            String status = operation.getStatus() == null ? null : operation.getStatus().name();
            events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                    operation.getCreatedAt(), "OPERATION", operation.getId(), status,
                    type + (status == null ? "" : " · " + status)));
            if (status != null && operation.getType() != null) {
                facts.add(operation.getType().name() + ": " + status);
            }

            List<BusinessOperationEvent> persisted = operationEvents
                    .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(runtimeId, operation.getId());
            for (int index = persisted.size() - 1; index >= 0; index--) {
                BusinessOperationEvent event = persisted.get(index);
                events.add(new PlatformDemoTimelineResponse.TimelineEvent(
                        event.getCreatedAt(), "OPERATION_EVENT", event.getId(),
                        event.getStatus() == null ? null : event.getStatus().name(),
                        clean(event.getEventType()) == null ? "Persisted operation event" : event.getEventType()));
            }
        }

        events.sort(Comparator.comparing(
                PlatformDemoTimelineResponse.TimelineEvent::at,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<String> followUps = new ArrayList<>();
        String proofState;
        if (facts.isEmpty()) {
            proofState = "REVIEW_REQUIRED";
            followUps.add("No persisted outcome, successful action, or operation evidence is available for this demo session.");
        } else {
            proofState = "RECORDED_VALUE";
        }

        return new PlatformDemoTimelineResponse(
                session.getId(), runtimeId, session.getState(), List.copyOf(events),
                new PlatformDemoTimelineResponse.ProofOfValue(
                        proofState, scopedCalls.size(), scopedConversations.size(),
                        scopedOperations.size(), List.copyOf(facts), List.copyOf(followUps)));
    }

    private UUID requireConfiguredRuntime() {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) throw new IllegalStateException("No server-owned DEMO runtime is configured");
        return runtimeId;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
