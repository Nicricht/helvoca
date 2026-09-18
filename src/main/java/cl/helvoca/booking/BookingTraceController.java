package cl.helvoca.booking;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BookingTraceController {
    private final BookingTraceService service;

    public BookingTraceController(BookingTraceService service) {
        this.service = service;
    }

    @GetMapping("/{bookingId}/trace")
    public BookingTraceService.TraceView trace(@PathVariable UUID bookingId) {
        return service.trace(bookingId);
    }
}
