package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class BookingConfirmationPostCommitLogIntegrationTest {
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
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired ServiceItemRepository services;
    @Autowired BookingConfirmationWorkflowService workflow;

    @Test
    void emitsStructuredBookingConfirmedEvidenceOnlyAfterCommit(CapturedOutput output) {
        Fixture fixture = fixture();
        Instant startAt = futureBusinessTime(5);
        UUID sourceReferenceId = UUID.randomUUID();

        JSONObject proposal = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                BookingSource.AI_WHATSAPP,
                new JSONObject()
                        .put("serviceId", fixture.service().getId().toString())
                        .put("startAt", startAt.toString()));

        assertTrue(proposal.getBoolean("success"));
        JSONObject proposalData = proposal.getJSONObject("data");
        UUID operationId = UUID.fromString(proposalData.getString("operationId"));
        UUID token = UUID.fromString(proposalData.getString("confirmationToken"));

        JSONObject confirmation = workflow.execute(
                fixture.business().getId(),
                fixture.customer().getId(),
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.WHATSAPP,
                BookingSource.AI_WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString()));

        assertTrue(confirmation.getBoolean("success"));
        String bookingId = confirmation.getJSONObject("data").getString("bookingId");

        assertFalse(output.getOut().contains("BOOKING_CONFIRMED"),
                "The evidence must not be emitted before the database transaction commits");

        TestTransaction.flagForCommit();
        TestTransaction.end();

        String committedOutput = output.getOut();
        assertTrue(committedOutput.contains("BOOKING_CONFIRMED"));
        assertTrue(committedOutput.contains("businessId=" + fixture.business().getId()));
        assertTrue(committedOutput.contains("bookingId=" + bookingId));
        assertTrue(committedOutput.contains("operationId=" + operationId));
        assertTrue(committedOutput.contains("startAt=" + startAt));
        assertTrue(committedOutput.contains("source=AI_WHATSAPP"));
    }

    private Fixture fixture() {
        Business business = new Business();
        business.setName("Post-commit booking audit tenant");
        business.setTimezone("America/Santiago");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Cliente de prueba");
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

    private static Instant futureBusinessTime(int daysAhead) {
        return ZonedDateTime.now(ZoneId.of("America/Santiago"))
                .plusDays(daysAhead)
                .withHour(12)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }

    private record Fixture(Business business, Customer customer, ServiceItem service) {}
}
