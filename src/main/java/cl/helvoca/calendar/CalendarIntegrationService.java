package cl.helvoca.calendar;

import cl.helvoca.booking.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarIntegrationService {
    private final CalendarIntegrationStore integrations;
    private final BookingCalendarEventStore events;
    private final CalendarProviderRegistry providers;
    private final BookingRepository bookings;
    private final CalendarSyncOutboxService calendarSync;

    public CalendarIntegrationService(CalendarIntegrationStore integrations,
                                      BookingCalendarEventStore events,
                                      CalendarProviderRegistry providers,
                                      BookingRepository bookings,
                                      CalendarSyncOutboxService calendarSync) {
        this.integrations = integrations;
        this.events = events;
        this.providers = providers;
        this.bookings = bookings;
        this.calendarSync = calendarSync;
    }

    @Transactional(readOnly = true)
    public Optional<CalendarIntegration> current(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        return integrations.findByBusinessId(businessId);
    }

    /**
     * Provider adapters call this only after they have authenticated and
     * authorized the tenant against the external calendar account.
     */
    @Transactional
    public CalendarIntegration activateAuthorizedConnection(UUID businessId,
                                                            String providerCode,
                                                            String externalCalendarId,
                                                            boolean meetingsEnabled) {
        CalendarProvider provider = providers.require(businessId, providerCode);
        CalendarIntegration current = integrations.findByBusinessId(businessId).orElse(null);
        String normalizedCalendarId = externalCalendarId == null ? null : externalCalendarId.trim();

        boolean bindingChanged = current != null
                && (!current.providerCode().equalsIgnoreCase(provider.code())
                || !Objects.equals(current.externalCalendarId(), normalizedCalendarId));
        if (bindingChanged && events.activeExternalEvents(businessId) > 0) {
            throw new IllegalStateException(
                    "Existing external calendar events must be reconciled before changing provider or calendar");
        }

        CalendarIntegration connected = integrations.markConnected(
                businessId,
                provider.code(),
                normalizedCalendarId,
                meetingsEnabled);

        // Connection/settings changes must converge existing bookings, not only
        // future mutations. The V37 outbox deduplicates unchanged desired state.
        bookings.findAllByBusinessIdOrderByStartAtDesc(businessId)
                .forEach(calendarSync::enqueueIfConnected);
        return connected;
    }
}
