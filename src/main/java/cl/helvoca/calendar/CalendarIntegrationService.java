package cl.helvoca.calendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarIntegrationService {
    private final CalendarIntegrationStore integrations;
    private final BookingCalendarEventStore events;
    private final CalendarProviderRegistry providers;

    public CalendarIntegrationService(CalendarIntegrationStore integrations,
                                      BookingCalendarEventStore events,
                                      CalendarProviderRegistry providers) {
        this.integrations = integrations;
        this.events = events;
        this.providers = providers;
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
        if (events.activeExternalEventsForOtherProvider(businessId, provider.code()) > 0) {
            throw new IllegalStateException("Existing external calendar events must be reconciled before changing provider");
        }
        return integrations.markConnected(
                businessId,
                provider.code(),
                externalCalendarId,
                meetingsEnabled);
    }
}
