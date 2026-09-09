package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@DataJpaTest
class BookingRepositoryIntegrationTest {

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
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired ServiceItemRepository services;
    @Autowired BookingRepository bookings;

    @Test
    void flywayAndOverlapQueryWorkAgainstPostgres() {
        Business business = new Business();
        business.setName("Integration Test Business");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Test Customer");
        customer.setPhone("+56911111111");
        customer = customers.saveAndFlush(customer);

        ServiceItem service = new ServiceItem();
        service.setBusinessId(business.getId());
        service.setName("Test Service");
        service.setDurationMinutes(60);
        service = services.saveAndFlush(service);

        Instant start = Instant.parse("2030-01-01T12:00:00Z");
        Instant end = Instant.parse("2030-01-01T13:00:00Z");

        Booking booking = new Booking();
        booking.setBusinessId(business.getId());
        booking.setCustomerId(customer.getId());
        booking.setServiceId(service.getId());
        booking.setStartAt(start);
        booking.setEndAt(end);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.ADMIN);
        bookings.saveAndFlush(booking);

        long overlap = bookings.countOverlaps(
                business.getId(),
                service.getId(),
                Instant.parse("2030-01-01T12:30:00Z"),
                Instant.parse("2030-01-01T13:30:00Z"),
                BookingStatus.CANCELLED,
                null
        );

        long noOverlap = bookings.countOverlaps(
                business.getId(),
                service.getId(),
                Instant.parse("2030-01-01T13:00:00Z"),
                Instant.parse("2030-01-01T14:00:00Z"),
                BookingStatus.CANCELLED,
                null
        );

        assertEquals(1L, overlap);
        assertEquals(0L, noOverlap);
    }
}
