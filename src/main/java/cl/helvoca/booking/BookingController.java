package cl.helvoca.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasAuthority('PERM_BOOKINGS_READ')")
public class BookingController {
    private final BookingService service;
    private final BookingContextService contextService;
    private final BookingActivityService activityService;
    private final BookingLifecycleService lifecycleService;
    private final BookingPaymentService paymentService;

    public BookingController(
            BookingService service,
            BookingContextService contextService,
            BookingActivityService activityService,
            BookingLifecycleService lifecycleService,
            BookingPaymentService paymentService) {
        this.service = service;
        this.contextService = contextService;
        this.activityService = activityService;
        this.lifecycleService = lifecycleService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<BookingResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/context")
    public BookingContextResponse context(@PathVariable UUID id) { return contextService.get(id); }

    @GetMapping("/{id}/activity")
    public List<BookingActivityResponse> activity(@PathVariable UUID id) { return activityService.list(id); }

    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @RequestParam UUID serviceId,
            @RequestParam Instant startAt,
            @RequestParam(required = false) UUID excludeBookingId) {
        return service.availability(serviceId, startAt, excludeBookingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public BookingResponse reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return service.reschedule(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public BookingResponse complete(@PathVariable UUID id) {
        return lifecycleService.complete(id);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public BookingResponse noShow(@PathVariable UUID id) {
        return lifecycleService.noShow(id);
    }

    @GetMapping("/{id}/payment")
    public BookingPaymentService.BookingPaymentSummary payment(@PathVariable UUID id) {
        return paymentService.summary(id);
    }

    @PostMapping("/{id}/payments/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public BookingPaymentService.ManualPaymentResponse recordManualPayment(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody BookingPaymentService.ManualPaymentRequest request) {
        return paymentService.recordManual(id, request, idempotencyKey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_BOOKINGS_MANAGE')")
    public void cancel(@PathVariable UUID id) { service.cancel(id); }
}
