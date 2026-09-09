package cl.helvoca.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) { this.service = service; }

    @GetMapping
    public List<BookingResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @RequestParam UUID serviceId,
            @RequestParam Instant startAt) {
        return service.availability(serviceId, startAt);
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
