package cl.helvoca.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformDemoTimelineResponse(
        UUID sessionId,
        DemoSessionState state,
        int callCount,
        Instant refreshedAt,
        List<Item> items) {

    public record Item(
            String kind,
            Instant at,
            String title,
            String detail,
            String status,
            UUID callId,
            UUID entityId) {}
}
