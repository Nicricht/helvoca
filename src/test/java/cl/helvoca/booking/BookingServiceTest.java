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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        verify(auditService, never()).humanSuccess(any(), any(), any(), any(), any(), any());
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
        verify(auditService, never()).humanSuccess(
                any(), eq("BOOKING_RESCHEDULE"), any(), any(), any(), any());
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
        verify(auditService, never()).humanSuccess(
                any(), eq("BOOKING_CREATE"), any(), any(), any(), any());
    }

    @Test
    void createAuditsAfterSnapshot() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(7200);
        Instant endAt = startAt.plusSeconds(3600);

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
        when(saved.getEndAt()).thenReturn(endAt);
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

        service.create(new CreateBookingRequest(customerId, serviceId, startAt, BookingSource.ADMIN, "private note"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("BOOKING_CREATE"),
                eq("BOOKING"),
                eq(bookingId),
                isNull(),
                afterCaptor.capture());

        Map<String, Object> after = afterCaptor.getValue();
        assertEquals(startAt.toString(), after.get("startAt"));
        assertEquals("CONFIRMED", after.get("status"));
        assertEquals(customerId.toString(), after.get("customerId"));
        assertFalse(after.containsKey("notes"));
    }

    @Test
    void rescheduleAuditsBeforeAndAfterSnapshots() {
        UUID businessId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant oldStart = Instant.now().plusSeconds(7200);
        Instant newStart = oldStart.plusSeconds(3600);

        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceCatalogService catalog = mock(ServiceCatalogService.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setCustomerId(customerId);
        booking.setServiceId(serviceId);
        booking.setStartAt(oldStart);
        booking.setEndAt(oldStart.plusSeconds(3600));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.ADMIN);
        ServiceItem item = serviceItem(businessId, 60);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(bookings.findByIdAndBusinessId(bookingId, businessId)).thenReturn(Optional.of(booking));
        when(catalog.requireActiveEntity(serviceId, businessId)).thenReturn(item);
        when(schedule.isWithinBusinessHours(eq(businessId), eq(newStart), any(Instant.class))).thenReturn(true);
        when(bookings.countOverlaps(
                eq(businessId), eq(serviceId), eq(newStart), any(Instant.class),
                eq(BookingStatus.CANCELLED), eq(bookingId))).thenReturn(0L);

        BookingService service = new BookingService(
                bookings, customers, catalog, schedule, tenantProvider, auditService);

        service.reschedule(bookingId, new RescheduleBookingRequest(newStart, "private note"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("BOOKING_RESCHEDULE"),
                eq("BOOKING"),
                eq(bookingId),
                beforeCaptor.capture(),
                afterCaptor.capture());

        assertEquals(oldStart.toString(), beforeCaptor.getValue().get("startAt"));
        assertEquals(newStart.toString(), afterCaptor.getValue().get("startAt"));
        assertEquals("CONFIRMED", beforeCaptor.getValue().get("status"));
        assertEquals("CONFIRMED", afterCaptor.getValue().get("status"));
        assertFalse(afterCaptor.getValue().containsKey("notes"));
    }

    @Test
    void cancelAuditsStatusBeforeAndAfter() {
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).humanSuccess(
                eq(businessId),
                eq("BOOKING_CANCEL"),
                eq("BOOKING"),
                eq(bookingId),
                beforeCaptor.capture(),
                afterCaptor.capture());

        assertEquals("CONFIRMED", beforeCaptor.getValue().get("status"));
        assertEquals("CANCELLED", afterCaptor.getValue().get("status"));
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void humanAuditUsesAuthenticatedJwtClaimsAndSnapshots() {
        UUID businessId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);
        Map<String, Object> before = Map.of("status", "CONFIRMED");
        Map<String, Object> after = Map.of("status", "CANCELLED");

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
                businessId, "BOOKING_CANCEL", "BOOKING", UUID.randomUUID(), before, after);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertEquals("HUMAN", log.getActorType());
        assertEquals(userId, log.getActorUserId());
        assertEquals("Carolina Soto", log.getActorName());
        assertEquals("carolina@example.com", log.getActorEmail());
        assertEquals("OPERATOR", log.getActorRole());
        assertEquals(before, log.getBeforeState());
        assertEquals(after, log.getAfterState());
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
                        UUID.randomUUID(),
                        "BOOKING_CANCEL",
                        "BOOKING",
                        UUID.randomUUID(),
                        Map.of("status", "CONFIRMED"),
                        Map.of("status", "CANCELLED")));
        verify(repository, never()).save(any());
    }

    @Test
    void legacyHumanAuditWithoutSnapshotsStillWorks() {
        UUID businessId = UUID.randomUUID();
        AuditLogRepository repository = mock(AuditLogRepository.class);

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", UUID.randomUUID().toString())
                .claim("business_id", businessId.toString())
                .claim("name", "Carolina Soto")
                .claim("email", "carolina@example.com")
                .claim("roles", List.of("BUSINESS_ADMIN"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        jwt,
                        "test-token",
                        List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));

        new AuditService(repository).humanSuccess(
                businessId, "BOOKING_CREATE", "BOOKING", UUID.randomUUID());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getBeforeState());
        assertNull(captor.getValue().getAfterState());
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
