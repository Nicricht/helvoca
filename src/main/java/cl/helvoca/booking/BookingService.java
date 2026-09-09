package cl.helvoca.booking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.schedule.SchedulePolicyService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceCatalogService;
import cl.helvoca.servicecatalog.ServiceItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final ServiceCatalogService catalog;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;
    private final SchedulePolicyService schedulePolicy;

    public BookingService(
            BookingRepository bookings,
            CustomerRepository customers,
            ServiceCatalogService catalog,
            TenantProvider tenantProvider,
            AuditService auditService,
            SchedulePolicyService schedulePolicy) {
        this.bookings = bookings;
        this.customers = customers;
        this.catalog = catalog;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
        this.schedulePolicy = schedulePolicy;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list() {
        UUID businessId = tenantProvider.requireBusinessId();
        return bookings.findAllByBusinessIdOrderByStartAtDesc(businessId)
                .stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        return BookingResponse.from(requireBooking(id, businessId));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID serviceId, Instant startAt) {
        UUID businessId = tenantProvider.requireBusinessId();
        validateFuture(startAt);
        ServiceItem service = catalog.requireActiveEntity(serviceId, businessId);
        Instant endAt = calculateEnd(startAt, service);
        boolean available = schedulePolicy.isOpen(businessId, startAt, endAt)
                && !hasOverlap(businessId, serviceId, startAt, endAt, null);
        return new AvailabilityResponse(serviceId, startAt, endAt, available);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponse create(CreateBookingRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        validateFuture(request.startAt());
        requireCustomer(request.customerId(), businessId);
        ServiceItem service = catalog.requireActiveEntity(request.serviceId(), businessId);
        Instant endAt = calculateEnd(request.startAt(), service);

        if (!schedulePolicy.isOpen(businessId, request.startAt(), endAt)) {
            throw new ConflictException("BUSINESS_CLOSED");
        }
        if (hasOverlap(businessId, request.serviceId(), request.startAt(), endAt, null)) {
            throw new ConflictException("BOOKING_SLOT_UNAVAILABLE");
        }

        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setCustomerId(request.customerId());
        booking.setServiceId(request.serviceId());
        booking.setStartAt(request.startAt());
        booking.setEndAt(endAt);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(request.source() == null ? BookingSource.ADMIN : request.source());
        booking.setNotes(request.notes());

        Booking saved = bookings.save(booking);
        auditService.success(businessId, "BOOKING_CREATE", "BOOKING", saved.getId());
        return BookingResponse.from(saved);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponse reschedule(UUID id, RescheduleBookingRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        validateFuture(request.startAt());
        Booking booking = requireBooking(id, businessId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Cancelled bookings cannot be rescheduled");
        }

        ServiceItem service = catalog.requireActiveEntity(booking.getServiceId(), businessId);
        Instant endAt = calculateEnd(request.startAt(), service);
        if (!schedulePolicy.isOpen(businessId, request.startAt(), endAt)) {
            throw new ConflictException("BUSINESS_CLOSED");
        }
        if (hasOverlap(businessId, booking.getServiceId(), request.startAt(), endAt, id)) {
            throw new ConflictException("BOOKING_SLOT_UNAVAILABLE");
        }

        booking.setStartAt(request.startAt());
        booking.setEndAt(endAt);
        booking.setNotes(request.notes());
        auditService.success(businessId, "BOOKING_RESCHEDULE", "BOOKING", id);
        return BookingResponse.from(booking);
    }

    @Transactional
    public void cancel(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        Booking booking = requireBooking(id, businessId);
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.CANCELLED);
            auditService.success(businessId, "BOOKING_CANCEL", "BOOKING", id);
        }
    }

    private Booking requireBooking(UUID id, UUID businessId) {
        return bookings.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private void requireCustomer(UUID id, UUID businessId) {
        customers.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private boolean hasOverlap(UUID businessId, UUID serviceId, Instant startAt, Instant endAt, UUID excludeId) {
        return bookings.countOverlaps(
                businessId, serviceId, startAt, endAt, BookingStatus.CANCELLED, excludeId) > 0;
    }

    private static Instant calculateEnd(Instant startAt, ServiceItem service) {
        return startAt.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
    }

    private static void validateFuture(Instant startAt) {
        if (!startAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("startAt must be in the future");
        }
    }
}
