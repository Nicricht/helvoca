package cl.helvoca.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformDemoTimelineResponse(
        UUID sessionId,
        UUID runtimeBusinessId,
        DemoSessionState sessionStatus,
        List<TimelineEvent> events,
        ProofOfValue proofOfValue) {

    public record TimelineEvent(
            Instant at,
            String type,
            UUID entityId,
            String status,
            String detail) {}

    public record ProofOfValue(
            String state,
            int calls,
            int conversations,
            int operations,
            List<String> facts,
            List<String> followUps) {}
}
