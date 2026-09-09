package cl.helvoca.booking;

import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(
        UUID serviceId,
        Instant startAt,
        Instant endAt,
        boolean available
) {}
