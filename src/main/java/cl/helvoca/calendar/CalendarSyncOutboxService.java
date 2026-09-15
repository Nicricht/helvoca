package cl.helvoca.calendar;

import cl.helvoca.booking.Booking;
import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class CalendarSyncOutboxService {
    private final CalendarIntegrationStore integrations;
    private final BookingCalendarEventStore events;
    private final PersistentJobService jobs;

    public CalendarSyncOutboxService(CalendarIntegrationStore integrations,
                                     BookingCalendarEventStore events,
                                     PersistentJobService jobs) {
        this.integrations = integrations;
        this.events = events;
        this.jobs = jobs;
    }

    @Transactional
    public Optional<PersistentJob> enqueueIfConnected(Booking booking) {
        if (booking == null || booking.getBusinessId() == null || booking.getId() == null) {
            throw new IllegalArgumentException("Persisted booking is required for calendar synchronization");
        }
        CalendarIntegration integration = integrations.findConnected(booking.getBusinessId()).orElse(null);
        if (integration == null) return Optional.empty();

        String fingerprint = fingerprint(booking, integration);
        BookingCalendarEvent event = events.prepare(integration, booking, fingerprint);
        String key = "calendar-event-sync:" + booking.getId() + ":v" + event.desiredVersion();
        String payload = new JSONObject()
                .put("calendarEventId", event.id().toString())
                .put("desiredVersion", event.desiredVersion())
                .toString();

        return Optional.of(jobs.enqueue(
                booking.getBusinessId(),
                booking.getOperationId(),
                PersistentJob.Type.CALENDAR_EVENT_SYNC,
                key,
                payload));
    }

    static String fingerprint(Booking booking, CalendarIntegration integration) {
        String canonical = String.join("|",
                integration.id().toString(),
                integration.providerCode(),
                integration.externalCalendarId() == null ? "" : integration.externalCalendarId(),
                Boolean.toString(integration.meetingsEnabled()),
                booking.getId().toString(),
                booking.getOperationId() == null ? "" : booking.getOperationId().toString(),
                booking.getServiceId().toString(),
                booking.getCustomerId().toString(),
                booking.getStatus().name(),
                instant(booking.getStartAt()),
                instant(booking.getEndAt()),
                booking.getNotes() == null ? "" : booking.getNotes());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }
}
