package cl.helvoca.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID customerId,
        UUID serviceId,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        BookingSource source,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getCustomerId(), booking.getServiceId(),
                booking.getStartAt(), booking.getEndAt(), booking.getStatus(),
                booking.getSource(), booking.getNotes(), booking.getCreatedAt(), booking.getUpdatedAt()
        );
    }
}
