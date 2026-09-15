package cl.helvoca.calendar;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class CalendarEventSyncJobHandler implements PersistentJobHandler {
    private final BookingCalendarEventStore events;
    private final CalendarIntegrationStore integrations;
    private final CalendarProviderRegistry providers;
    private final BookingRepository bookings;
    private final ServiceItemRepository services;
    private final CustomerRepository customers;

    public CalendarEventSyncJobHandler(BookingCalendarEventStore events,
                                       CalendarIntegrationStore integrations,
                                       CalendarProviderRegistry providers,
                                       BookingRepository bookings,
                                       ServiceItemRepository services,
                                       CustomerRepository customers) {
        this.events = events;
        this.integrations = integrations;
        this.providers = providers;
        this.bookings = bookings;
        this.services = services;
        this.customers = customers;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.CALENDAR_EVENT_SYNC;
    }

    @Override
    public void handle(PersistentJob job) {
        UUID eventId;
        int desiredVersion;
        try {
            JSONObject payload = new JSONObject(job.payloadJson());
            eventId = UUID.fromString(payload.getString("calendarEventId"));
            desiredVersion = payload.getInt("desiredVersion");
            if (desiredVersion < 1) throw new IllegalArgumentException("desiredVersion must be positive");
        } catch (Exception e) {
            throw new PermanentJobException("Calendar durable job payload is invalid", e);
        }

        BookingCalendarEvent event = events.findById(job.businessId(), eventId)
                .orElseThrow(() -> new PermanentJobException("Calendar event projection not found"));
        if (event.desiredVersion() != desiredVersion) {
            // A newer booking mutation already superseded this durable job.
            return;
        }

        CalendarIntegration integration = integrations.findConnected(job.businessId())
                .orElseThrow(() -> new PermanentJobException("Calendar integration is not connected"));
        if (!integration.id().equals(event.integrationId())
                || !integration.providerCode().equalsIgnoreCase(event.providerCode())) {
            throw new PermanentJobException("Calendar integration changed before event synchronization");
        }

        Booking booking = bookings.findByIdAndBusinessId(event.bookingId(), job.businessId())
                .orElseThrow(() -> new PermanentJobException("Booking not found for calendar synchronization"));
        CalendarProvider provider;
        try {
            provider = providers.require(job.businessId(), integration.providerCode());
        } catch (RuntimeException e) {
            events.markFailed(job.businessId(), event.id(), desiredVersion,
                    "CALENDAR_PROVIDER_UNAVAILABLE", e.getMessage());
            throw new PermanentJobException("Calendar provider is unavailable for tenant", e);
        }

        String idempotencyKey = "calendar-booking:" + booking.getId() + ":v" + desiredVersion;
        try {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                if (event.externalEventId() != null && !event.externalEventId().isBlank()) {
                    provider.delete(new CalendarProvider.DeleteCommand(
                            job.businessId(),
                            integration.externalCalendarId(),
                            booking.getId(),
                            event.externalEventId(),
                            idempotencyKey + ":delete"));
                }
                events.markDeleted(job.businessId(), event.id(), desiredVersion);
                return;
            }

            ServiceItem service = services.findByIdAndBusinessId(booking.getServiceId(), job.businessId())
                    .orElseThrow(() -> new PermanentJobException("Booking service no longer exists"));
            Customer customer = customers.findByIdAndBusinessId(booking.getCustomerId(), job.businessId())
                    .orElseThrow(() -> new PermanentJobException("Booking customer no longer exists"));

            CalendarProvider.UpsertResult result = provider.upsert(new CalendarProvider.UpsertCommand(
                    job.businessId(),
                    integration.externalCalendarId(),
                    booking.getId(),
                    event.externalEventId(),
                    service.getName(),
                    description(customer, booking),
                    booking.getStartAt(),
                    booking.getEndAt(),
                    integration.meetingsEnabled(),
                    idempotencyKey));
            if (result == null || blank(result.externalEventId())) {
                throw new CalendarProvider.ProviderException("Provider did not return an external event id", true);
            }

            String meetingUrl = null;
            if (integration.meetingsEnabled()) {
                meetingUrl = requireHttpsMeetingUrl(result.meetingUrl());
            }
            events.markSynced(job.businessId(), event.id(), desiredVersion,
                    result.externalEventId().trim(), meetingUrl);
        } catch (PermanentJobException e) {
            events.markFailed(job.businessId(), event.id(), desiredVersion,
                    "CALENDAR_SYNC_INVALID_STATE", e.getMessage());
            throw e;
        } catch (CalendarProvider.ProviderException e) {
            events.markFailed(job.businessId(), event.id(), desiredVersion,
                    e.retryable() ? "CALENDAR_PROVIDER_TEMPORARY_FAILURE" : "CALENDAR_PROVIDER_FAILURE",
                    e.getMessage());
            if (e.retryable()) throw new RetryableJobException(e.getMessage(), e);
            throw new PermanentJobException(e.getMessage(), e);
        } catch (RuntimeException e) {
            events.markFailed(job.businessId(), event.id(), desiredVersion,
                    "CALENDAR_PROVIDER_TEMPORARY_FAILURE", e.getMessage());
            throw new RetryableJobException("Calendar provider execution failed", e);
        }
    }

    private static String description(Customer customer, Booking booking) {
        String customerName = customer.getName() == null || customer.getName().isBlank()
                ? "Cliente" : customer.getName().trim();
        String notes = booking.getNotes() == null || booking.getNotes().isBlank()
                ? "" : " | " + booking.getNotes().trim();
        return "Helvoca booking " + booking.getId() + " | " + customerName + notes;
    }

    private static String requireHttpsMeetingUrl(String raw) {
        if (blank(raw)) {
            throw new CalendarProvider.ProviderException("Meeting creation was enabled but provider returned no meeting URL", true);
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("not https");
            }
            return uri.toString();
        } catch (Exception e) {
            throw new CalendarProvider.ProviderException("Provider returned an unsafe meeting URL", false, e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
