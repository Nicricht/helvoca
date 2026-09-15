package cl.helvoca.calendar;

import java.time.Instant;
import java.util.UUID;

public record CalendarIntegration(
        UUID id,
        UUID businessId,
        String providerCode,
        Status status,
        String externalCalendarId,
        boolean meetingsEnabled,
        Instant connectedAt,
        Instant disconnectedAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        DISCONNECTED, CONNECTED, ERROR
    }
}
