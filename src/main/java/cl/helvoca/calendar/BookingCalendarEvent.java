package cl.helvoca.calendar;

import java.time.Instant;
import java.util.UUID;

public record BookingCalendarEvent(
        UUID id,
        UUID businessId,
        UUID bookingId,
        UUID integrationId,
        String providerCode,
        String externalEventId,
        String meetingUrl,
        int desiredVersion,
        int syncedVersion,
        String desiredFingerprint,
        Status status,
        String lastErrorCode,
        String lastErrorMessage,
        Instant syncedAt,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        PENDING, SYNCED, FAILED, DELETED
    }
}
