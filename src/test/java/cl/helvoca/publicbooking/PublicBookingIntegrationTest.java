package cl.helvoca.publicbooking;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class PublicBookingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired PublicBookingService publicBooking;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessProfileRepository profiles;
    @Autowired ServiceItemRepository services;
    @Autowired BusinessHourRepository hours;
    @Autowired BookingRepository bookings;
    @Autowired CustomerRepository customers;
    @Autowired TenantDatabaseContext databaseContext;

    TenantDatabaseContext.Scope systemScope;

    @BeforeEach
    void enterSystemFixtureScope() {
        systemScope = databaseContext.useSystem();
    }

    @AfterEach
    void leaveSystemFixtureScope() {
        if (systemScope != null) systemScope.close();
    }

    @Test
    void publicPageExposesOnlyPublicTenantData() {
        Fixture fixture = fixture(true);

        var page = publicBooking.page(fixture.key());

        assertEquals("Public Booking Test", page.name());
        assertEquals("America/Santiago", page.timezone());
        assertEquals(1, page.services().size());
        assertEquals(fixture.service().getId(), page.services().getFirst().id());
    }

    @Test
    void disabledOrUnknownPublicKeyFailsClosed() {
        Fixture disabled = fixture(false);

        assertThrows(NotFoundException.class, () -> publicBooking.page(disabled.key()));
        assertThrows(NotFoundException.class, () -> publicBooking.page(UUID.randomUUID()));
    }

    @Test
    void availabilityCannotUseAnotherTenantsService() {
        Fixture first = fixture(true);
        Fixture second = fixture(true);

        assertThrows(
                NotFoundException.class,
                () -> publicBooking.availability(first.key(), second.service().getId(), futureDate()));
    }

    @Test
    void availabilityReturnsAuthoritativeTenantSlots() {
        Fixture fixture = fixture(true);

        var availability = publicBooking.availability(
                fixture.key(), fixture.service().getId(), futureDate());

        assertEquals("America/Santiago", availability.timezone());
        assertFalse(availability.slots().isEmpty());
        assertTrue(availability.slots().stream()
                .allMatch(slot -> slot.startAt().isBefore(slot.endAt())));
    }

    @Test
    void arbitraryTimeOutsideAuthoritativeSlotGridIsRejected() {
        Fixture fixture = fixture(true);
        Instant arbitrary = futureDate()
                .atTime(10, 15)
                .atZone(ZoneId.of("America/Santiago"))
                .toInstant();

        assertThrows(
                ConflictException.class,
                () -> publicBooking.create(
                        fixture.key(),
                        "idem-arbitrary-slot-0001",
                        request(
                                fixture.service().getId(),
                                arbitrary,
                                "Cliente Arbitrario",
                                "+56911116666")));
    }

    @Test
    void publicCreateIsIdempotentAndCreatesConfirmedWebBooking() {
        Fixture fixture = fixture(true);
        Instant startAt = publicStartAt();
        String key = "idem-public-booking-0001";
        var request = request(fixture.service().getId(), startAt, "Ana Web", "+56911112222");

        var first = publicBooking.create(fixture.key(), key, request);
        var second = publicBooking.create(fixture.key(), key, request);

        assertEquals(first.id(), second.id());
        assertEquals("CONFIRMED", first.status());

        List<Booking> tenantBookings = bookings.findAllByBusinessIdOrderByStartAtDesc(
                fixture.business().getId());
        assertEquals(1, tenantBookings.size());
        assertEquals(BookingSource.PUBLIC_WEB, tenantBookings.getFirst().getSource());
        assertEquals(1, customers.findAllByBusinessIdOrderByCreatedAtDesc(
                fixture.business().getId()).size());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentRequestIsRejected() {
        Fixture fixture = fixture(true);
        Instant firstStart = publicStartAt();
        String key = "idem-public-booking-0002";

        publicBooking.create(
                fixture.key(),
                key,
                request(fixture.service().getId(), firstStart, "Ana Web", "+56911113333"));

        assertThrows(
                ConflictException.class,
                () -> publicBooking.create(
                        fixture.key(),
                        key,
                        request(
                                fixture.service().getId(),
                                firstStart.plusSeconds(1800),
                                "Ana Web",
                                "+56911113333")));
    }

    @Test
    void concurrentCustomersCannotDoubleBookTheSamePublicSlot() throws Exception {
        Fixture fixture = fixture(true);
        Instant startAt = publicStartAt();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> performCreate(
                    fixture, "idem-concurrent-public-a", "Ana Uno", "+56911114444", startAt, ready, fire));
            Future<String> second = executor.submit(() -> performCreate(
                    fixture, "idem-concurrent-public-b", "Ana Dos", "+56911115555", startAt, ready, fire));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            List<String> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
            assertEquals(1, bookings.countOverlaps(
                    fixture.business().getId(),
                    fixture.service().getId(),
                    startAt,
                    startAt.plusSeconds(1800),
                    cl.helvoca.booking.BookingStatus.CANCELLED,
                    null));
        } finally {
            executor.shutdownNow();
        }
    }

    private String performCreate(Fixture fixture,
                                 String idempotencyKey,
                                 String name,
                                 String phone,
                                 Instant startAt,
                                 CountDownLatch ready,
                                 CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(5, TimeUnit.SECONDS);
        try {
            publicBooking.create(
                    fixture.key(),
                    idempotencyKey,
                    request(fixture.service().getId(), startAt, name, phone));
            return "CREATED";
        } catch (ConflictException expected) {
            return "CONFLICT";
        }
    }

    private Fixture fixture(boolean enabled) {
        Business business = new Business();
        business.setName("Public Booking Test");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);

        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(business.getId());
        profile.setPublicDescription("Reserva tu hora en línea");
        profile.setAddressLine("Av. Demo 123");
        profile.setCity("Santiago");
        profile.setDefaultCurrency("CLP");
        profile.setUsesReservations(true);
        profile.setPublicBookingEnabled(enabled);
        profile = profiles.saveAndFlush(profile);

        ServiceItem service = new ServiceItem();
        service.setBusinessId(business.getId());
        service.setName("Consulta web");
        service.setDescription("Consulta pública");
        service.setDurationMinutes(30);
        service.setPrice(java.math.BigDecimal.valueOf(25_000));
        service.setActive(true);
        service = services.saveAndFlush(service);

        LocalDate date = futureDate();
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(business.getId());
        hour.setDayOfWeek(date.getDayOfWeek().getValue());
        hour.setOpenTime(LocalTime.of(9, 0));
        hour.setCloseTime(LocalTime.of(18, 0));
        hours.saveAndFlush(hour);

        return new Fixture(business, profile.getPublicBookingKey(), service);
    }

    private static LocalDate futureDate() {
        return LocalDate.now(ZoneId.of("America/Santiago")).plusDays(2);
    }

    private static Instant publicStartAt() {
        return futureDate().atTime(10, 0).atZone(ZoneId.of("America/Santiago")).toInstant();
    }

    private static PublicBookingController.PublicBookingRequest request(
            UUID serviceId,
            Instant startAt,
            String name,
            String phone) {
        return new PublicBookingController.PublicBookingRequest(
                serviceId,
                startAt,
                new PublicBookingController.CustomerInput(
                        name,
                        phone,
                        "web@example.cl"));
    }

    private record Fixture(Business business, UUID key, ServiceItem service) {}
}
