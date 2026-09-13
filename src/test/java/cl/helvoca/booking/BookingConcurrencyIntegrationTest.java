package cl.helvoca.booking;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class BookingConcurrencyIntegrationTest {

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

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired ServiceItemRepository services;
    @Autowired BusinessHourRepository hours;
    @Autowired CallSessionRepository calls;
    @Autowired BookingRepository bookings;
    @Autowired RealtimeToolService tools;

    @Test
    void simultaneousCallsCannotDoubleBookSameServiceAndTime() throws Exception {
        ZoneId zone = ZoneId.of("America/Santiago");
        LocalDate date = LocalDate.now(zone).plusDays(1);

        Business business = new Business();
        business.setName("Concurrency Test Business");
        business.setTimezone(zone.getId());
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Concurrent Customer");
        customer.setPhone("+56910000001");
        customer = customers.saveAndFlush(customer);

        ServiceItem service = new ServiceItem();
        service.setBusinessId(business.getId());
        service.setName("Concurrent Service");
        service.setDurationMinutes(30);
        service.setActive(true);
        service = services.saveAndFlush(service);

        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(business.getId());
        hour.setDayOfWeek(date.getDayOfWeek().getValue());
        hour.setOpenTime(LocalTime.of(9, 0));
        hour.setCloseTime(LocalTime.of(18, 0));
        hours.saveAndFlush(hour);

        CallSession firstCall = call(business.getId(), customer.getId(), "concurrency-call-1", "MZ-concurrency-1");
        CallSession secondCall = call(business.getId(), customer.getId(), "concurrency-call-2", "MZ-concurrency-2");
        firstCall = calls.saveAndFlush(firstCall);
        secondCall = calls.saveAndFlush(secondCall);

        Instant startAt = date.atTime(10, 0).atZone(zone).toInstant();
        Instant endAt = startAt.plusSeconds(1800);
        JSONObject args = new JSONObject()
                .put("serviceId", service.getId().toString())
                .put("startAt", startAt.toString());

        RealtimeCallContext firstContext = context(firstCall, business.getId(), customer.getId(), customer.getPhone());
        RealtimeCallContext secondContext = context(secondCall, business.getId(), customer.getId(), customer.getPhone());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JSONObject> first = executor.submit(() -> executeConcurrent(firstContext, args, ready, fire));
            Future<JSONObject> second = executor.submit(() -> executeConcurrent(secondContext, args, ready, fire));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            List<JSONObject> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            long successes = results.stream().filter(r -> r.optBoolean("success", false)).count();
            long slotRejected = results.stream()
                    .filter(r -> !r.optBoolean("success", false))
                    .filter(r -> {
                        JSONObject error = r.optJSONObject("error");
                        return error != null && "BOOKING_SLOT_UNAVAILABLE".equals(error.optString("code"));
                    })
                    .count();

            assertEquals(1L, successes);
            assertEquals(1L, slotRejected);
            assertEquals(1L, bookings.countOverlaps(
                    business.getId(), service.getId(), startAt, endAt, BookingStatus.CANCELLED, null));
        } finally {
            executor.shutdownNow();
        }
    }

    private JSONObject executeConcurrent(RealtimeCallContext context,
                                         JSONObject args,
                                         CountDownLatch ready,
                                         CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(5, TimeUnit.SECONDS);
        return new JSONObject(tools.execute(context, "create_booking", args.toString()));
    }

    private static CallSession call(UUID businessId, UUID customerId, String providerId, String streamSid) {
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setCustomerId(customerId);
        call.setTelephonyProvider("test");
        call.setAiProvider("test");
        call.setProviderCallId(providerId);
        call.setCallerNumber("+56910000001");
        call.setDestinationNumber("+56220000001");
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now());
        call.setAnsweredAt(Instant.now());
        call.setStreamSid(streamSid);
        call.setStreamStartedAt(Instant.now());
        return call;
    }

    private static RealtimeCallContext context(CallSession call, UUID businessId, UUID customerId, String caller) {
        return new RealtimeCallContext(
                call.getId(), businessId, customerId, caller, call.getDestinationNumber(), call.getStreamSid());
    }
}
