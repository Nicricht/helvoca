package cl.helvoca.publicbooking;

import cl.helvoca.audit.AuditService;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PublicBookingTenantService {
    private final BusinessRepository businesses;
    private final BusinessProfileRepository profiles;
    private final ServiceItemRepository services;
    private final BusinessScheduleService schedule;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final BookingConfirmationWorkflowService bookingWorkflow;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public PublicBookingTenantService(BusinessRepository businesses,
                                      BusinessProfileRepository profiles,
                                      ServiceItemRepository services,
                                      BusinessScheduleService schedule,
                                      CustomerRepository customers,
                                      BookingRepository bookings,
                                      BookingConfirmationWorkflowService bookingWorkflow,
                                      AuditService auditService,
                                      JdbcTemplate jdbc) {
        this.businesses = businesses;
        this.profiles = profiles;
        this.services = services;
        this.schedule = schedule;
        this.customers = customers;
        this.bookings = bookings;
        this.bookingWorkflow = bookingWorkflow;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PublicBookingController.PublicBookingPageResponse page(UUID businessId) {
        Business business = requireBusiness(businessId);
        BusinessProfile profile = profiles.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Booking page not found"));
        if (!profile.isPublicBookingEnabled()) {
            throw new NotFoundException("Booking page not found");
        }

        String currency = blank(profile.getDefaultCurrency()) ? "CLP" : profile.getDefaultCurrency();
        List<PublicBookingController.PublicServiceResponse> publicServices =
                services.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                        .filter(ServiceItem::isActive)
                        .map(item -> new PublicBookingController.PublicServiceResponse(
                                item.getId(),
                                item.getName(),
                                item.getDescription(),
                                item.getDurationMinutes(),
                                item.getPrice(),
                                currency))
                        .toList();

        return new PublicBookingController.PublicBookingPageResponse(
                business.getName(),
                business.getTimezone(),
                profile.getPublicDescription(),
                address(profile),
                publicServices);
    }

    @Transactional(readOnly = true)
    public PublicBookingController.PublicAvailabilityResponse availability(UUID businessId,
                                                                          UUID serviceId,
                                                                          LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date is required");
        ServiceItem service = services.findByIdAndBusinessId(serviceId, businessId)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new NotFoundException("Service not found"));

        BusinessScheduleService.DailyAvailability available = schedule.listAvailableSlots(
                businessId, serviceId, service.getDurationMinutes(), date, 24);

        List<PublicBookingController.PublicSlotResponse> slots = available.slots().stream()
                .map(slot -> new PublicBookingController.PublicSlotResponse(slot.startAt(), slot.endAt()))
                .toList();

        return new PublicBookingController.PublicAvailabilityResponse(
                date, available.timezone(), slots);
    }

    @Transactional
    public PublicBookingController.PublicBookingConfirmation create(
            UUID businessId,
            String idempotencyKey,
            PublicBookingController.PublicBookingRequest request) {
        String safeKey = normalizeIdempotencyKey(idempotencyKey);
        String phone = request.customer().phone().trim();
        String name = request.customer().name().trim();
        String email = blank(request.customer().email())
                ? null
                : request.customer().email().trim().toLowerCase(Locale.ROOT);
        String requestHash = sha256(
                request.serviceId() + "|" + request.startAt() + "|" + name + "|" + phone + "|" + String.valueOf(email));

        advisoryLock(businessId, safeKey.hashCode());
        ExistingSubmission existing = findSubmission(businessId, safeKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new ConflictException("Idempotency key already used for a different booking request.");
            }
            return confirmationForExisting(businessId, existing.bookingId());
        }

        ServiceItem service = services.findByIdAndBusinessId(request.serviceId(), businessId)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new NotFoundException("Service not found"));

        advisoryLock(businessId, phone.hashCode());
        Customer customer = customers.findFirstRawByBusinessIdAndPhone(businessId, phone)
                .orElseGet(() -> {
                    Customer created = new Customer();
                    created.setBusinessId(businessId);
                    created.setName(name);
                    created.setPhone(phone);
                    created.setEmail(email);
                    return customers.saveAndFlush(created);
                });

        JSONObject proposalArgs = new JSONObject()
                .put("serviceId", service.getId().toString())
                .put("startAt", request.startAt().toString());
        JSONObject proposal = bookingWorkflow.execute(
                businessId,
                customer.getId(),
                null,
                phone,
                BusinessOrder.Source.API,
                BookingSource.PUBLIC_WEB,
                proposalArgs);
        JSONObject proposalData = requireWorkflowSuccess(proposal);

        JSONObject confirmationArgs = new JSONObject()
                .put("operationId", proposalData.getString("operationId"))
                .put("confirmationToken", proposalData.getString("confirmationToken"));
        JSONObject confirmed = bookingWorkflow.execute(
                businessId,
                customer.getId(),
                null,
                phone,
                BusinessOrder.Source.API,
                BookingSource.PUBLIC_WEB,
                confirmationArgs);
        JSONObject confirmedData = requireWorkflowSuccess(confirmed);

        UUID bookingId = UUID.fromString(confirmedData.getString("bookingId"));
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new IllegalStateException("Confirmed booking projection missing"));

        jdbc.update("""
                INSERT INTO public_booking_submission
                    (business_id, idempotency_key, request_hash, booking_id)
                VALUES (?, ?, ?, ?)
                """, businessId, safeKey, requestHash, bookingId);

        auditService.success(businessId, "PUBLIC_BOOKING_CREATE", "BOOKING", bookingId);
        return confirmation(businessId, booking, service, customer.getName());
    }

    private PublicBookingController.PublicBookingConfirmation confirmationForExisting(
            UUID businessId, UUID bookingId) {
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        ServiceItem service = services.findByIdAndBusinessId(booking.getServiceId(), businessId)
                .orElseThrow(() -> new NotFoundException("Service not found"));
        Customer customer = customers.findByIdAndBusinessId(booking.getCustomerId(), businessId)
                .orElse(null);
        return confirmation(
                businessId,
                booking,
                service,
                customer == null ? null : customer.getName());
    }

    private PublicBookingController.PublicBookingConfirmation confirmation(
            UUID businessId,
            Booking booking,
            ServiceItem service,
            String customerName) {
        Business business = requireBusiness(businessId);
        return new PublicBookingController.PublicBookingConfirmation(
                booking.getId(),
                booking.getStatus().name(),
                service.getName(),
                booking.getStartAt(),
                business.getTimezone(),
                customerName);
    }

    private ExistingSubmission findSubmission(UUID businessId, String idempotencyKey) {
        return jdbc.query("""
                SELECT request_hash, booking_id
                  FROM public_booking_submission
                 WHERE business_id = ? AND idempotency_key = ?
                """,
                rs -> rs.next()
                        ? new ExistingSubmission(rs.getString(1), rs.getObject(2, UUID.class))
                        : null,
                businessId,
                idempotencyKey);
    }

    private JSONObject requireWorkflowSuccess(JSONObject result) {
        if (result.optBoolean("success", false)) {
            return result.getJSONObject("data");
        }
        JSONObject error = result.optJSONObject("error");
        String code = error == null ? "BOOKING_FAILED" : error.optString("code", "BOOKING_FAILED");
        String message = error == null
                ? "No pudimos crear la reserva."
                : error.optString("message", "No pudimos crear la reserva.");

        if ("SERVICE_NOT_FOUND".equals(code) || "BOOKING_OPERATION_NOT_FOUND".equals(code)) {
            throw new NotFoundException(message);
        }
        if ("BOOKING_SLOT_UNAVAILABLE".equals(code)
                || "BUSINESS_CLOSED".equals(code)
                || "CONFIRMATION_EXPIRED".equals(code)
                || "STALE_CONFIRMATION".equals(code)) {
            throw new ConflictException(message);
        }
        throw new IllegalArgumentException(message);
    }

    private Business requireBusiness(UUID businessId) {
        return businesses.findById(businessId)
                .orElseThrow(() -> new NotFoundException("Business not found"));
    }

    private void advisoryLock(UUID businessId, int resourceHash) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessId.hashCode() + "," + resourceHash + ")");
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null) throw new IllegalArgumentException("Idempotency-Key is required.");
        String normalized = value.trim();
        if (normalized.length() < 8 || normalized.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 8 and 128 characters.");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String address(BusinessProfile profile) {
        return java.util.stream.Stream.of(
                        profile.getAddressLine(),
                        profile.getCommune(),
                        profile.getCity(),
                        profile.getRegion())
                .filter(value -> !blank(value))
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ExistingSubmission(String requestHash, UUID bookingId) {}
}
