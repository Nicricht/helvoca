package cl.helvoca.publicbooking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/booking-pages")
public class PublicBookingController {
    private final PublicBookingService service;

    public PublicBookingController(PublicBookingService service) {
        this.service = service;
    }

    @GetMapping("/{key}")
    public PublicBookingPageResponse page(@PathVariable UUID key) {
        return service.page(key);
    }

    @GetMapping("/{key}/availability")
    public PublicAvailabilityResponse availability(@PathVariable UUID key,
                                                   @RequestParam UUID serviceId,
                                                   @RequestParam LocalDate date) {
        return service.availability(key, serviceId, date);
    }

    @PostMapping("/{key}/bookings")
    public ResponseEntity<PublicBookingConfirmation> create(@PathVariable UUID key,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @Valid @RequestBody PublicBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(key, idempotencyKey, request));
    }

    public record PublicBookingRequest(
            @NotNull UUID serviceId,
            @NotNull @Future Instant startAt,
            @NotNull @Valid CustomerInput customer
    ) {}

    public record CustomerInput(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "phone must use E.164 format")
            String phone,
            @Email @Size(max = 180) String email
    ) {}

    public record PublicBookingPageResponse(
            String name,
            String timezone,
            String description,
            String address,
            List<PublicServiceResponse> services
    ) {}

    public record PublicServiceResponse(
            UUID id,
            String name,
            String description,
            int durationMinutes,
            java.math.BigDecimal price,
            String currency
    ) {}

    public record PublicAvailabilityResponse(
            LocalDate date,
            String timezone,
            List<PublicSlotResponse> slots
    ) {}

    public record PublicSlotResponse(Instant startAt, Instant endAt) {}

    public record PublicBookingConfirmation(
            UUID id,
            String status,
            String serviceName,
            Instant startAt,
            String timezone,
            String customerName
    ) {}
}
