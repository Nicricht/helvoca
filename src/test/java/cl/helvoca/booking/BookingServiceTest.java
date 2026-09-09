package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.SchedulePolicyService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceCatalogService;
import cl.helvoca.servicecatalog.ServiceItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    @Test
    void createRejectsOverlappingSlot() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(3600);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        SchedulePolicyService schedulePolicy = mock(SchedulePolicyService.class);

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        ServiceItem item = serviceItem(businessId);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedulePolicy.isOpen(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), isNull()))
                .thenReturn(1L);

        BookingService service = new BookingService(
                bookings, customers, catalog, tenantProvider, auditService, schedulePolicy);

        CreateBookingRequest request = new CreateBookingRequest(
                customerId, serviceId, startAt, BookingSource.ADMIN, null);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(bookings, never()).save(any());
    }

    @Test
    void createRejectsClosedBusiness() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);
        SchedulePolicyService schedulePolicy = mock(SchedulePolicyService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(new Customer()));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(serviceItem(businessId));
        when(schedulePolicy.isOpen(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, tenantProvider, auditService, schedulePolicy);

        CreateBookingRequest request = new CreateBookingRequest(
                customerId, serviceId, startAt, BookingSource.ADMIN, null);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(bookings, never()).save(any());
    }

    private static ServiceItem serviceItem(UUID businessId) {
        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName("Consulta");
        item.setDurationMinutes(60);
        item.setActive(true);
        return item;
    }
}
