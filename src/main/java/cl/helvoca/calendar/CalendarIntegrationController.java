package cl.helvoca.calendar;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class CalendarIntegrationController {
    private final CalendarIntegrationService integrations;
    private final BookingCalendarEventStore events;
    private final TenantProvider tenantProvider;

    public CalendarIntegrationController(CalendarIntegrationService integrations,
                                         BookingCalendarEventStore events,
                                         TenantProvider tenantProvider) {
        this.integrations = integrations;
        this.events = events;
        this.tenantProvider = tenantProvider;
    }

    @GetMapping("/integration")
    public ResponseEntity<IntegrationView> integration() {
        UUID businessId = tenantProvider.requireBusinessId();
        return integrations.current(businessId)
                .map(value -> ResponseEntity.ok(IntegrationView.from(value)))
                .orElseGet(() -> ResponseEntity.ok(IntegrationView.disconnected()));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventView>> events() {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(events.recent(businessId).stream().map(EventView::from).toList());
    }

    public record IntegrationView(String provider,
                                  String status,
                                  String externalCalendarId,
                                  boolean meetingsEnabled,
                                  Instant connectedAt,
                                  String lastErrorCode) {
        static IntegrationView from(CalendarIntegration integration) {
            return new IntegrationView(
                    integration.providerCode(), integration.status().name(), integration.externalCalendarId(),
                    integration.meetingsEnabled(), integration.connectedAt(), integration.lastErrorCode());
        }

        static IntegrationView disconnected() {
            return new IntegrationView(null, CalendarIntegration.Status.DISCONNECTED.name(), null, false, null, null);
        }
    }

    public record EventView(UUID id,
                            UUID bookingId,
                            String provider,
                            String status,
                            int desiredVersion,
                            int syncedVersion,
                            String externalEventId,
                            String meetingUrl,
                            String lastErrorCode,
                            Instant syncedAt,
                            Instant updatedAt) {
        static EventView from(BookingCalendarEvent event) {
            return new EventView(
                    event.id(), event.bookingId(), event.providerCode(), event.status().name(),
                    event.desiredVersion(), event.syncedVersion(), event.externalEventId(), event.meetingUrl(),
                    event.lastErrorCode(), event.syncedAt(), event.updatedAt());
        }
    }
}
