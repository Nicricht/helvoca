package cl.helvoca.booking;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID customerId,
        @NotNull UUID serviceId,
        @NotNull Instant startAt,
        BookingSource source,
        String notes
) {}
