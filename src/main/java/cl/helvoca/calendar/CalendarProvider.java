package cl.helvoca.calendar;

import java.time.Instant;
import java.util.UUID;

public interface CalendarProvider {
    String code();

    boolean supports(UUID businessId);

    UpsertResult upsert(UpsertCommand command);

    void delete(DeleteCommand command);

    record UpsertCommand(
            UUID businessId,
            String externalCalendarId,
            UUID bookingId,
            String externalEventId,
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean createMeeting,
            String idempotencyKey) { }

    record UpsertResult(String externalEventId, String meetingUrl) { }

    record DeleteCommand(
            UUID businessId,
            String externalCalendarId,
            UUID bookingId,
            String externalEventId,
            String idempotencyKey) { }

    final class ProviderException extends RuntimeException {
        private final boolean retryable;

        public ProviderException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public ProviderException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() { return retryable; }
    }
}
