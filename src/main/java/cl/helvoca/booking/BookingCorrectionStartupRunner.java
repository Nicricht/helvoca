package cl.helvoca.booking;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BookingCorrectionStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BookingCorrectionStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String bookingId;
    private final String targetStartAt;
    private final TenantDatabaseContext databaseContext;
    private final BookingRepository bookings;
    private final BookingService bookingService;

    public BookingCorrectionStartupRunner(
            @Value("${HELVOCA_BOOKING_CORRECTION_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_BOOKING_CORRECTION_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_BOOKING_CORRECTION_BOOKING_ID:}") String bookingId,
            @Value("${HELVOCA_BOOKING_CORRECTION_START_AT:}") String targetStartAt,
            TenantDatabaseContext databaseContext,
            BookingRepository bookings,
            BookingService bookingService) {
        this.enabled = enabled;
        this.businessId = clean(businessId);
        this.bookingId = clean(bookingId);
        this.targetStartAt = clean(targetStartAt);
        this.databaseContext = databaseContext;
        this.bookings = bookings;
        this.bookingService = bookingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId = parseUuid(businessId, "HELVOCA_BOOKING_CORRECTION_BUSINESS_ID");
        UUID targetBookingId = parseUuid(bookingId, "HELVOCA_BOOKING_CORRECTION_BOOKING_ID");
        Instant target = parseInstant(targetStartAt, "HELVOCA_BOOKING_CORRECTION_START_AT");

        databaseContext.runAsTenant(tenantId, () -> {
            CorrectionResult result = correct(tenantId, targetBookingId, target);
            log.warn(
                    "BOOKING_CORRECTION_COMPLETE businessId={} bookingId={} previousStartAt={} targetStartAt={} actualStartAt={} idempotent={}",
                    tenantId,
                    targetBookingId,
                    result.previousStartAt(),
                    target,
                    result.actualStartAt(),
                    result.idempotent());
        });
    }

    CorrectionResult correct(UUID businessId, UUID bookingId, Instant targetStartAt) {
        Booking existing = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new IllegalStateException("Target booking not found"));

        Instant previous = existing.getStartAt();
        if (targetStartAt.equals(previous)) {
            return new CorrectionResult(previous, previous, true);
        }

        BookingResponse updated = bookingService.rescheduleForOperationalCorrection(
                businessId,
                bookingId,
                new RescheduleBookingRequest(targetStartAt, existing.getNotes()));

        if (!bookingId.equals(updated.id())) {
            throw new IllegalStateException("Booking correction changed booking identity");
        }
        if (!targetStartAt.equals(updated.startAt())) {
            throw new IllegalStateException("Booking correction did not reach target startAt");
        }
        return new CorrectionResult(previous, updated.startAt(), false);
    }

    private static UUID parseUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalStateException(name + " must be a valid UUID", e);
        }
    }

    private static Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalStateException(name + " must be a valid ISO-8601 instant", e);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    record CorrectionResult(Instant previousStartAt, Instant actualStartAt, boolean idempotent) {}
}
