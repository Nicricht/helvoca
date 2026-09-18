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
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BookingController {
    private final BookingService service;
    private final BookingContextService contextService;
    private final BookingActivityService activityService;

    public BookingController(
            BookingService service,
            BookingContextService contextService,
            BookingActivityService activityService) {
        this.service = service;
        this.contextService = contextService;
        this.activityService = activityService;
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
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public BookingResponse reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return service.reschedule(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) { service.cancel(id); }
}
