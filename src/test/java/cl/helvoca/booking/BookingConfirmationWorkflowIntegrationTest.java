package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.OperationConfirmation;
import cl.helvoca.operations.OperationConfirmationRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class BookingConfirmationWorkflowIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
    @Autowired BookingRepository bookings;
    @Autowired BusinessOperationRepository operations;
    @Autowired OperationConfirmationRepository confirmations;
    @Autowired BookingConfirmationWorkflowService workflow;
    @Autowired EntityManager entityManager;

    @Test
    void proposalDoesNotCreateBookingAndSameCustomerCanConfirmOnAnotherChannel() {
        Fixture fixture = fixture();
        Instant startAt = futureBusinessTime(3);
        UUID voiceSource = UUID.randomUUID();

        JSONObject proposal = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                voiceSource,
                "+56911111111",
                BusinessOrder.Source.VOICE,
                BookingSource.AI_CALL,
                new JSONObject()
                        .put("serviceId", fixture.service().getId().toString())
                        .put("startAt", startAt.toString())
                        .put("notes", "primera visita"));

        assertTrue(proposal.getBoolean("success"));
        JSONObject proposed = proposal.getJSONObject("data");
        assertTrue(proposed.getBoolean("requiresConfirmation"));
        assertFalse(proposed.getBoolean("bookingCreated"));
        assertFalse(proposed.has("bookingId"));
        assertEquals(0, bookings.count());

        UUID operationId = UUID.fromString(proposed.getString("operationId"));
        UUID token = UUID.fromString(proposed.getString("confirmationToken"));
        BusinessOperation awaiting = operations.findByIdAndBusinessId(operationId, fixture.business().getId()).orElseThrow();
        assertEquals(BusinessOperation.Status.AWAITING_CONFIRMATION, awaiting.getStatus());
        assertEquals(fixture.customer().getId(), awaiting.getCustomerId());

        UUID whatsappSource = UUID.randomUUID();
        JSONObject confirmed = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                whatsappSource,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                BookingSource.AI_WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString())
                        // These are deliberately hostile changes. The token must bind
                        // confirmation to the persisted proposal instead of trusting
                        // new conditions supplied by the second channel.
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", Instant.now().plus(10, ChronoUnit.DAYS).toString()));

        assertTrue(confirmed.getBoolean("success"));
        JSONObject data = confirmed.getJSONObject("data");
        UUID bookingId = UUID.fromString(data.getString("bookingId"));
        Booking booking = bookings.findByIdAndBusinessId(bookingId, fixture.business().getId()).orElseThrow();
        assertEquals(operationId, booking.getOperationId());
        assertEquals(fixture.service().getId(), booking.getServiceId());
        assertEquals(startAt, booking.getStartAt());
        assertEquals(fixture.customer().getId(), booking.getCustomerId());
        assertEquals(BookingSource.AI_WHATSAPP, booking.getSource());

        BusinessOperation materialized = operations.findByIdAndBusinessId(operationId, fixture.business().getId()).orElseThrow();
        assertEquals(BusinessOperation.Status.CONFIRMED, materialized.getStatus());
        assertNull(materialized.getConfirmationToken());

        OperationConfirmation consumed = confirmations
                .findByBusinessIdAndToken(fixture.business().getId(), token)
                .orElseThrow();
        assertEquals(OperationConfirmation.State.CONSUMED, consumed.getState());
        assertEquals(BusinessOrder.Source.WHATSAPP, consumed.getResolvedChannel());
        assertEquals(whatsappSource, consumed.getResolvedSourceReferenceId());

        JSONObject replay = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.VOICE,
                BookingSource.AI_CALL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString()));

        assertTrue(replay.getBoolean("success"));
        assertTrue(replay.getJSONObject("data").getBoolean("idempotentReplay"));
        assertEquals(bookingId.toString(), replay.getJSONObject("data").getString("bookingId"));
        assertEquals(1, bookings.count());
    }

    @Test
    void slotOccupiedAfterProposalExpiresConfirmationWithoutCreatingSecondBooking() {
        Fixture fixture = fixture();
        Instant startAt = futureBusinessTime(4);

        JSONObject proposal = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.VOICE,
                BookingSource.AI_CALL,
                new JSONObject()
                        .put("serviceId", fixture.service().getId().toString())
                        .put("startAt", startAt.toString()));
        assertTrue(proposal.getBoolean("success"));
        UUID operationId = UUID.fromString(proposal.getJSONObject("data").getString("operationId"));
        UUID token = UUID.fromString(proposal.getJSONObject("data").getString("confirmationToken"));

        Booking competitor = new Booking();
        competitor.setBusinessId(fixture.business().getId());
        competitor.setCustomerId(fixture.customer().getId());
        competitor.setServiceId(fixture.service().getId());
        competitor.setStartAt(startAt);
        competitor.setEndAt(startAt.plus(fixture.service().getDurationMinutes(), ChronoUnit.MINUTES));
        competitor.setStatus(BookingStatus.CONFIRMED);
        competitor.setSource(BookingSource.ADMIN);
        bookings.saveAndFlush(competitor);

        JSONObject confirmation = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                BookingSource.AI_WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString()));

        assertFalse(confirmation.getBoolean("success"));
        assertEquals("BOOKING_SLOT_UNAVAILABLE",
                confirmation.getJSONObject("error").getString("code"));
        assertTrue(bookings.findByOperationIdAndBusinessId(operationId, fixture.business().getId()).isEmpty());
        assertEquals(1, bookings.count());

        BusinessOperation expired = operations.findByIdAndBusinessId(operationId, fixture.business().getId()).orElseThrow();
        assertEquals(BusinessOperation.Status.EXPIRED, expired.getStatus());
        assertNull(expired.getConfirmationToken());
        OperationConfirmation expiredConfirmation = confirmations
                .findByBusinessIdAndToken(fixture.business().getId(), token)
                .orElseThrow();
        entityManager.refresh(expiredConfirmation);
        assertEquals(OperationConfirmation.State.EXPIRED, expiredConfirmation.getState());
    }

    private static Instant futureBusinessTime(int daysAhead) {
        return ZonedDateTime.now(ZoneId.of("America/Santiago"))
                .plusDays(daysAhead)
                .withHour(12)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }

    private Fixture fixture() {
        Business business = new Business();
        business.setName("Booking confirmation tenant");
        business.setTimezone("America/Santiago");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Cliente");
        customer.setPhone("+56911111111");
        customer = customers.saveAndFlush(customer);

        ServiceItem service = new ServiceItem();
        service.setBusinessId(business.getId());
        service.setName("Consulta");
        service.setDescription("Consulta de prueba");
        service.setDurationMinutes(60);
        service.setPrice(new BigDecimal("25000.00"));
        service.setActive(true);
        service = services.saveAndFlush(service);

        return new Fixture(business, customer, service);
    }

    private record Fixture(Business business, Customer customer, ServiceItem service) {}
}
