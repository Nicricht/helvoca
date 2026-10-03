package cl.helvoca.publicbooking;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
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

    @Autowired MockMvc mvc;
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
    void anonymousPageExposesOnlyPublicTenantDataAndInternalBookingsStayProtected() throws Exception {
        Fixture fixture = fixture(true);

        mvc.perform(get("/api/v1/public/booking-pages/{key}", fixture.key()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Public Booking Test"))
                .andExpect(jsonPath("$.timezone").value("America/Santiago"))
                .andExpect(jsonPath("$.services[0].id").value(fixture.service().getId().toString()))
                .andExpect(jsonPath("$.businessId").doesNotExist())
                .andExpect(jsonPath("$.services[0].businessId").doesNotExist());

        mvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledOrUnknownPublicKeyFailsClosed() throws Exception {
        Fixture disabled = fixture(false);

        mvc.perform(get("/api/v1/public/booking-pages/{key}", disabled.key()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/public/booking-pages/{key}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void availabilityCannotUseAnotherTenantsService() throws Exception {
        Fixture first = fixture(true);
        Fixture second = fixture(true);
        LocalDate date = futureDate();

        mvc.perform(get("/api/v1/public/booking-pages/{key}/availability", first.key())
                        .param("serviceId", second.service().getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void availabilityReturnsAuthoritativeTenantSlotsWithoutTenantIdentity() throws Exception {
        Fixture fixture = fixture(true);
        LocalDate date = futureDate();

        mvc.perform(get("/api/v1/public/booking-pages/{key}/availability", fixture.key())
                        .param("serviceId", fixture.service().getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Santiago"))
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.slots[0].startAt").exists())
                .andExpect(jsonPath("$.businessId").doesNotExist());
    }

    @Test
    void publicCreateIsIdempotentAndCreatesConfirmedWebBooking() throws Exception {
        Fixture fixture = fixture(true);
        Instant startAt = publicStartAt();
        String key = "idem-public-booking-0001";
        String payload = payload(fixture.service().getId(), startAt, "Ana Web", "+56911112222");

        String first = mvc.perform(post("/api/v1/public/booking-pages/{key}/bookings", fixture.key())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.serviceName").value("Consulta web"))
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/api/v1/public/booking-pages/{key}/bookings", fixture.key())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode firstJson =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(first);
        com.fasterxml.jackson.databind.JsonNode secondJson =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(second);
        assertEquals(firstJson.get("id").asText(), secondJson.get("id").asText());

        List<Booking> tenantBookings = bookings.findAllByBusinessIdOrderByStartAtDesc(fixture.business().getId());
        assertEquals(1, tenantBookings.size());
        assertEquals(BookingSource.PUBLIC_WEB, tenantBookings.getFirst().getSource());
        assertEquals(1, customers.findAllByBusinessIdOrderByCreatedAtDesc(fixture.business().getId()).size());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentRequestIsRejected() throws Exception {
        Fixture fixture = fixture(true);
        Instant firstStart = publicStartAt();
        String key = "idem-public-booking-0002";

        mvc.perform(post("/api/v1/public/booking-pages/{key}/bookings", fixture.key())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(fixture.service().getId(), firstStart, "Ana Web", "+56911113333")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/public/booking-pages/{key}/bookings", fixture.key())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(
                                fixture.service().getId(),
                                firstStart.plusSeconds(1800),
                                "Ana Web",
                                "+56911113333")))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentCustomersCannotDoubleBookTheSamePublicSlot() throws Exception {
        Fixture fixture = fixture(true);
        Instant startAt = publicStartAt();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> performCreate(
                    fixture, "idem-concurrent-public-a", "Ana Uno", "+56911114444", startAt, ready, fire));
            Future<Integer> second = executor.submit(() -> performCreate(
                    fixture, "idem-concurrent-public-b", "Ana Dos", "+56911115555", startAt, ready, fire));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            List<Integer> statuses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
            long created = statuses.stream().filter(status -> status == 201).count();
            long conflicted = statuses.stream().filter(status -> status == 409).count();
            assertEquals(1, created);
            assertEquals(1, conflicted);
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

    private int performCreate(Fixture fixture,
                              String idempotencyKey,
                              String name,
                              String phone,
                              Instant startAt,
                              CountDownLatch ready,
                              CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/public/booking-pages/{key}/bookings", fixture.key())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(fixture.service().getId(), startAt, name, phone)))
                .andReturn().getResponse().getStatus();
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

    private static String payload(UUID serviceId, Instant startAt, String name, String phone) {
        return """
                {
                  "serviceId":"%s",
                  "startAt":"%s",
                  "customer":{
                    "name":"%s",
                    "phone":"%s",
                    "email":"web@example.cl"
                  }
                }
                """.formatted(serviceId, startAt, name, phone);
    }

    private record Fixture(Business business, UUID key, ServiceItem service) {}
}
