package cl.helvoca.calendar;

import cl.helvoca.booking.*;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Import(CalendarSyncIntegrationTest.ProviderConfig.class)
class CalendarSyncIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.jobs.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired ServiceItemRepository services;
    @Autowired BusinessOperationRepository operations;
    @Autowired BookingRepository bookings;
    @Autowired BookingOperationSyncService bookingSync;
    @Autowired CalendarIntegrationService calendarIntegrations;
    @Autowired BookingCalendarEventStore calendarEvents;
    @Autowired PersistentJobService jobs;
    @Autowired FakeCalendarProvider provider;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM persistent_job");
        jdbc.update("DELETE FROM booking_calendar_event");
        jdbc.update("DELETE FROM calendar_integration");
        provider.reset();
    }

    @Test
    void createRescheduleAndCancelUseOneExternalEventAndBackendMeetingLink() {
        Fixture f = fixture();
        calendarIntegrations.activateAuthorizedConnection(f.business().getId(), "FAKE", "primary", true);

        bookingSync.synchronize(f.business().getId(), f.booking().getId(), UUID.randomUUID(),
                BusinessOrder.Source.WHATSAPP, "create_booking");
        BookingCalendarEvent pending = calendarEvents.findByBooking(f.business().getId(), f.booking().getId()).orElseThrow();
        assertEquals(1, pending.desiredVersion());
        assertEquals(BookingCalendarEvent.Status.PENDING, pending.status());
        assertEquals(PersistentJob.Type.CALENDAR_EVENT_SYNC, jobs.recent(f.business().getId()).getFirst().jobType());

        assertTrue(jobs.processOne("calendar-test-worker"));
        BookingCalendarEvent synced = calendarEvents.findByBooking(f.business().getId(), f.booking().getId()).orElseThrow();
        assertEquals(BookingCalendarEvent.Status.SYNCED, synced.status());
        assertEquals(1, synced.syncedVersion());
        assertEquals("evt-" + f.booking().getId(), synced.externalEventId());
        assertEquals("https://meet.fake/" + f.booking().getId(), synced.meetingUrl());

        Booking booking = bookings.findByIdAndBusinessId(f.booking().getId(), f.business().getId()).orElseThrow();
        booking.setStartAt(booking.getStartAt().plus(2, ChronoUnit.HOURS));
        booking.setEndAt(booking.getEndAt().plus(2, ChronoUnit.HOURS));
        bookings.saveAndFlush(booking);
        bookingSync.synchronize(f.business().getId(), booking.getId(), UUID.randomUUID(),
                BusinessOrder.Source.VOICE, "reschedule_booking");
        assertTrue(jobs.processOne("calendar-test-worker"));

        BookingCalendarEvent rescheduled = calendarEvents.findByBooking(f.business().getId(), booking.getId()).orElseThrow();
        assertEquals(2, rescheduled.desiredVersion());
        assertEquals(2, rescheduled.syncedVersion());
        assertEquals(synced.externalEventId(), rescheduled.externalEventId());
        assertEquals(2, provider.upserts.get());

        booking.setStatus(BookingStatus.CANCELLED);
        bookings.saveAndFlush(booking);
        bookingSync.synchronize(f.business().getId(), booking.getId(), UUID.randomUUID(),
                BusinessOrder.Source.WHATSAPP, "cancel_booking");
        assertTrue(jobs.processOne("calendar-test-worker"));

        BookingCalendarEvent deleted = calendarEvents.findByBooking(f.business().getId(), booking.getId()).orElseThrow();
        assertEquals(BookingCalendarEvent.Status.DELETED, deleted.status());
        assertEquals(3, deleted.syncedVersion());
        assertNull(deleted.meetingUrl());
        assertEquals(1, provider.deletes.get());
    }

    @Test
    void staleDurableVersionDoesNotOverwriteNewerBookingState() {
        Fixture f = fixture();
        calendarIntegrations.activateAuthorizedConnection(f.business().getId(), "FAKE", "primary", false);
        bookingSync.synchronize(f.business().getId(), f.booking().getId(), UUID.randomUUID(),
                BusinessOrder.Source.VOICE, "create_booking");

        Booking booking = bookings.findByIdAndBusinessId(f.booking().getId(), f.business().getId()).orElseThrow();
        Instant newestStart = booking.getStartAt().plus(4, ChronoUnit.HOURS);
        booking.setStartAt(newestStart);
        booking.setEndAt(booking.getEndAt().plus(4, ChronoUnit.HOURS));
        bookings.saveAndFlush(booking);
        bookingSync.synchronize(f.business().getId(), booking.getId(), UUID.randomUUID(),
                BusinessOrder.Source.WHATSAPP, "reschedule_booking");

        assertEquals(2, jobs.processBatch("calendar-version-worker", 10));
        assertEquals(1, provider.upserts.get(), "Only the newest desired version may call the provider");
        assertEquals(newestStart, provider.lastStart.get());
        BookingCalendarEvent event = calendarEvents.findByBooking(f.business().getId(), booking.getId()).orElseThrow();
        assertEquals(2, event.desiredVersion());
        assertEquals(2, event.syncedVersion());
    }

    @Test
    void unsafeProviderMeetingUrlIsNeverPersisted() {
        Fixture f = fixture();
        provider.meetingUrl.set("http://unsafe.example/meeting");
        calendarIntegrations.activateAuthorizedConnection(f.business().getId(), "FAKE", "primary", true);
        bookingSync.synchronize(f.business().getId(), f.booking().getId(), UUID.randomUUID(),
                BusinessOrder.Source.VOICE, "create_booking");

        assertTrue(jobs.processOne("calendar-security-worker"));
        BookingCalendarEvent event = calendarEvents.findByBooking(f.business().getId(), f.booking().getId()).orElseThrow();
        assertEquals(BookingCalendarEvent.Status.FAILED, event.status());
        assertNull(event.meetingUrl());
        assertEquals("CALENDAR_PROVIDER_FAILURE", event.lastErrorCode());
        assertEquals(PersistentJob.Status.DEAD_LETTER, jobs.recent(f.business().getId()).getFirst().status());
    }

    private Fixture fixture() {
        Business business = new Business();
        business.setName("Calendar tenant");
        business.setTimezone("America/Santiago");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Cliente Calendar");
        customer.setPhone("+56955550001");
        customer = customers.saveAndFlush(customer);

        ServiceItem service = new ServiceItem();
        service.setBusinessId(business.getId());
        service.setName("Reunión");
        service.setDescription("Reunión de prueba");
        service.setDurationMinutes(60);
        service.setPrice(new BigDecimal("15000"));
        service.setActive(true);
        service = services.saveAndFlush(service);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setCustomerId(customer.getId());
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.API);
        operation = operations.saveAndFlush(operation);

        Booking booking = new Booking();
        booking.setOperationId(operation.getId());
        booking.setBusinessId(business.getId());
        booking.setCustomerId(customer.getId());
        booking.setServiceId(service.getId());
        booking.setStartAt(Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES));
        booking.setEndAt(booking.getStartAt().plus(60, ChronoUnit.MINUTES));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.ADMIN);
        booking = bookings.saveAndFlush(booking);
        return new Fixture(business, customer, service, booking);
    }

    record Fixture(Business business, Customer customer, ServiceItem service, Booking booking) { }

    @TestConfiguration
    static class ProviderConfig {
        @Bean
        FakeCalendarProvider fakeCalendarProvider() {
            return new FakeCalendarProvider();
        }
    }

    static class FakeCalendarProvider implements CalendarProvider {
        final AtomicInteger upserts = new AtomicInteger();
        final AtomicInteger deletes = new AtomicInteger();
        final AtomicReference<Instant> lastStart = new AtomicReference<>();
        final AtomicReference<String> meetingUrl = new AtomicReference<>();

        void reset() {
            upserts.set(0);
            deletes.set(0);
            lastStart.set(null);
            meetingUrl.set(null);
        }

        @Override public String code() { return "FAKE"; }
        @Override public boolean supports(UUID businessId) { return true; }

        @Override
        public UpsertResult upsert(UpsertCommand command) {
            upserts.incrementAndGet();
            lastStart.set(command.startAt());
            String url = meetingUrl.get();
            if (url == null && command.createMeeting()) url = "https://meet.fake/" + command.bookingId();
            return new UpsertResult("evt-" + command.bookingId(), url);
        }

        @Override
        public void delete(DeleteCommand command) {
            deletes.incrementAndGet();
        }
    }
}
