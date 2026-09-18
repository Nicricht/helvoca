package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceCatalogService;
import cl.helvoca.servicecatalog.ServiceItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName("Consulta");
        item.setDurationMinutes(60);
        item.setActive(true);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), isNull()))
                .thenReturn(1L);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        CreateBookingRequest request = new CreateBookingRequest(
                customerId, serviceId, startAt, BookingSource.ADMIN, null);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(bookings, never()).save(any());
    }

    @Test
    void availabilityIsFalseOutsideBusinessHours() {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName("Consulta");
        item.setDurationMinutes(60);
        item.setActive(true);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        AvailabilityResponse response = service.availability(serviceId, startAt);

        assertFalse(response.available());
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rescheduleRejectsOutsideBusinessHours() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setServiceId(serviceId);
        booking.setStatus(BookingStatus.CONFIRMED);

        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName("Consulta");
        item.setDurationMinutes(60);
        item.setActive(true);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        assertThrows(ConflictException.class,
                () -> service.reschedule(bookingId, new RescheduleBookingRequest(startAt, null)));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
        verify(auditService, never()).success(any(), eq("BOOKING_RESCHEDULE"), any(), any());
    }
}
