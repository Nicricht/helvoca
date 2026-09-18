package cl.helvoca.booking;

import cl.helvoca.audit.AuditLog;
import cl.helvoca.audit.AuditLogRepository;
import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceCatalogService;
import cl.helvoca.servicecatalog.ServiceItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(true);
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
        verify(auditService, never()).humanSuccess(any(), any(), any(), any());
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

        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        AvailabilityResponse response = service.availability(serviceId, startAt, null);

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

        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        assertThrows(ConflictException.class,
                () -> service.reschedule(bookingId, new RescheduleBookingRequest(startAt, null)));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
        verify(auditService, never()).humanSuccess(any(), eq("BOOKING_RESCHEDULE"), any(), any());
    }

    @Test
    void availabilityCanExcludeCurrentBooking() {
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

        Booking current = new Booking();
        current.setBusinessId(businessId);
        current.setServiceId(serviceId);
        current.setStatus(BookingStatus.CONFIRMED);

        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(current));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), eq(bookingId)))
                .thenReturn(0L);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        AvailabilityResponse response = service.availability(serviceId, startAt, bookingId);

        org.junit.jupiter.api.Assertions.assertTrue(response.available());
        verify(bookings).countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), eq(bookingId));
    }

    @Test
    void createRejectsOutsideBusinessHours() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(false);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        CreateBookingRequest request = new CreateBookingRequest(
                customerId, serviceId, startAt, BookingSource.ADMIN, null);

        assertThrows(ConflictException.class, () -> service.create(request));
        verify(bookings, never()).countOverlaps(any(), any(), any(), any(), any(), any());
        verify(bookings, never()).save(any());
        verify(auditService, never()).humanSuccess(any(), eq("BOOKING_CREATE"), any(), any());
    }

    @Test
    void createUsesHumanAudit() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        ServiceItem item = serviceItem(businessId, 60);
        Booking saved = mock(Booking.class);
        when(saved.getId()).thenReturn(bookingId);
        when(saved.getCustomerId()).thenReturn(customerId);
        when(saved.getServiceId()).thenReturn(serviceId);
        when(saved.getStartAt()).thenReturn(startAt);
        when(saved.getEndAt()).thenReturn(startAt.plusSeconds(3600));
        when(saved.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(saved.getSource()).thenReturn(BookingSource.ADMIN);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), isNull())).thenReturn(0L);
        when(bookings.save(any(Booking.class))).thenReturn(saved);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        service.create(new CreateBookingRequest(customerId, serviceId, startAt, BookingSource.ADMIN, null));

        verify(auditService).humanSuccess(businessId, "BOOKING_CREATE", "BOOKING", bookingId);
    }

    @Test
    void rescheduleUsesHumanAudit() {
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
        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(startAt), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(startAt), any(Instant.class),
                eq(BookingStatus.CANCELLED), eq(bookingId))).thenReturn(0L);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        service.reschedule(bookingId, new RescheduleBookingRequest(startAt, "Cambio manual"));

        verify(auditService).humanSuccess(businessId, "BOOKING_RESCHEDULE", "BOOKING", bookingId);
    }

    @Test
    void cancelUsesHumanAudit() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        service.cancel(bookingId);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(auditService).humanSuccess(businessId, "BOOKING_CANCEL", "BOOKING", bookingId);
    }

    @Test
    void humanAuditUsesAuthenticatedJwtClaims() {
        UUID businessId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", userId.toString())
                .claim("business_id", businessId.toString())
                .claim("name", "Carolina Soto")
                .claim("email", "carolina@example.com")
                .claim("roles", List.of("OPERATOR"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        jwt,
                        "test-token",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));

        new AuditService(repository).humanSuccess(
                businessId, "BOOKING_CANCEL", "BOOKING", UUID.randomUUID());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertEquals("HUMAN", log.getActorType());
        assertEquals(userId, log.getActorUserId());
        assertEquals("Carolina Soto", log.getActorName());
        assertEquals("carolina@example.com", log.getActorEmail());
        assertEquals("OPERATOR", log.getActorRole());
    }

    @Test
    void humanAuditRejectsTenantMismatch() {
        UUID tokenBusinessId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", UUID.randomUUID().toString())
                .claim("business_id", tokenBusinessId.toString())
                .claim("name", "Carolina Soto")
                .claim("email", "carolina@example.com")
                .claim("roles", List.of("OPERATOR"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        jwt,
                        "test-token",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));

        AuditService auditService = new AuditService(repository);

        assertThrows(
                AccessDeniedException.class,
                () -> auditService.humanSuccess(
                        UUID.randomUUID(), "BOOKING_CANCEL", "BOOKING", UUID.randomUUID()));
        verify(repository, never()).save(any());
    }

    private static ServiceItem serviceItem(UUID businessId, int durationMinutes) {
        ServiceItem item = new ServiceItem();
        item.setBusinessId(businessId);
        item.setName("Consulta");
        item.setDurationMinutes(durationMinutes);
        item.setActive(true);
        return item;
    }
}
