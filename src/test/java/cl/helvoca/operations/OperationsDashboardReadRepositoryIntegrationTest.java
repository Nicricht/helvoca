package cl.helvoca.operations;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.learning.QuestionStatus;
import cl.helvoca.learning.UnansweredQuestion;
import cl.helvoca.learning.UnansweredQuestionRepository;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.RequestStatus;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
class OperationsDashboardReadRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

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
    @Autowired BookingRepository bookings;
    @Autowired BusinessOperationRepository operations;
    @Autowired BusinessRequestRepository requests;
    @Autowired UnansweredQuestionRepository questions;
    @Autowired TenantDatabaseContext databaseContext;

    TenantDatabaseContext.Scope systemScope;

    @BeforeEach
    void enterSystemScope() {
        systemScope = databaseContext.useSystem();
    }

    @AfterEach
    void leaveSystemScope() {
        if (systemScope != null) systemScope.close();
    }

    @Test
    void dashboardCountsAndTop10QueriesAreTenantScopedInPostgres() {
        Instant rangeStart = Instant.now().minusSeconds(10);
        Instant rangeEnd = Instant.now().plusSeconds(60);

        Business tenantA = business("Dashboard Tenant A");
        Business tenantB = business("Dashboard Tenant B");
        Customer customerA = customer(tenantA, "Cliente A");
        Customer customerB = customer(tenantB, "Cliente B");
        ServiceItem serviceA = service(tenantA, "Servicio A");
        ServiceItem serviceB = service(tenantB, "Servicio B");

        booking(tenantA, customerA, serviceA, BookingStatus.CONFIRMED);
        booking(tenantA, customerA, serviceA, BookingStatus.CANCELLED);
        booking(tenantB, customerB, serviceB, BookingStatus.CONFIRMED);

        createRequests(tenantA, 12);
        createRequests(tenantB, 1);
        createQuestions(tenantA, 12);
        createQuestions(tenantB, 1);

        assertEquals(1L,
                bookings.countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        tenantA.getId(), BookingStatus.CANCELLED, rangeStart, rangeEnd));
        assertEquals(1L,
                customers.countByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        tenantA.getId(), rangeStart, rangeEnd));
        assertEquals(12L,
                requests.countByBusinessIdAndStatusIn(
                        tenantA.getId(), List.of(RequestStatus.OPEN, RequestStatus.IN_PROGRESS)));
        assertEquals(12L,
                questions.countByBusinessIdAndStatus(tenantA.getId(), QuestionStatus.OPEN));

        assertEquals(10, requests.findTop10ByBusinessIdOrderByCreatedAtDesc(tenantA.getId()).size());
        assertEquals(10, questions.findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                tenantA.getId(), QuestionStatus.OPEN).size());

        assertEquals(1L,
                requests.countByBusinessIdAndStatusIn(
                        tenantB.getId(), List.of(RequestStatus.OPEN, RequestStatus.IN_PROGRESS)));
        assertEquals(1L,
                questions.countByBusinessIdAndStatus(tenantB.getId(), QuestionStatus.OPEN));
    }

    private Business business(String name) {
        Business value = new Business();
        value.setName(name);
        return businesses.saveAndFlush(value);
    }

    private Customer customer(Business business, String name) {
        Customer value = new Customer();
        value.setBusinessId(business.getId());
        value.setName(name);
        return customers.saveAndFlush(value);
    }

    private ServiceItem service(Business business, String name) {
        ServiceItem value = new ServiceItem();
        value.setBusinessId(business.getId());
        value.setName(name);
        value.setDurationMinutes(30);
        return services.saveAndFlush(value);
    }

    private void booking(Business business,
                         Customer customer,
                         ServiceItem service,
                         BookingStatus status) {
        Booking value = new Booking();
        value.setBusinessId(business.getId());
        value.setCustomerId(customer.getId());
        value.setServiceId(service.getId());
        value.setStartAt(Instant.now().plusSeconds(3600));
        value.setEndAt(Instant.now().plusSeconds(5400));
        value.setStatus(status);
        bookings.saveAndFlush(value);
    }

    private void createRequests(Business business, int count) {
        List<BusinessOperation> operationBatch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BusinessOperation operation = new BusinessOperation();
            operation.setBusinessId(business.getId());
            operation.setType(BusinessOperation.Type.REQUEST);
            operation.setStatus(BusinessOperation.Status.CONFIRMED);
            operation.setSource(BusinessOrder.Source.MANUAL);
            operationBatch.add(operation);
        }
        operationBatch = operations.saveAllAndFlush(operationBatch);

        List<BusinessRequest> requestBatch = new ArrayList<>(count);
        for (int i = 0; i < operationBatch.size(); i++) {
            BusinessRequest request = new BusinessRequest();
            request.setOperationId(operationBatch.get(i).getId());
            request.setBusinessId(business.getId());
            request.setRequestType("QUOTE");
            request.setTitle("Solicitud " + i);
            request.setStatus(RequestStatus.OPEN);
            requestBatch.add(request);
        }
        requests.saveAllAndFlush(requestBatch);
    }

    private void createQuestions(Business business, int count) {
        List<UnansweredQuestion> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UnansweredQuestion question = new UnansweredQuestion();
            question.setBusinessId(business.getId());
            question.setQuestion("Pregunta " + i);
            question.setNormalizedQuestion("pregunta-" + i);
            question.setStatus(QuestionStatus.OPEN);
            batch.add(question);
        }
        questions.saveAllAndFlush(batch);
    }
}
