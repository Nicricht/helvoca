package cl.helvoca.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleBookingRequest(
        @NotNull Instant startAt,
        String notes
) {}
