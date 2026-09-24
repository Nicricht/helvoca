package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Component
public class BookingTimeRepairStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BookingTimeRepairStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String bookingId;
    private final String expectedStartAt;
    private final String targetLocalDate;
    private final String targetLocalTime;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final BookingRepository bookings;
    private final BusinessRepository businesses;
    private final BusinessScheduleService schedule;
    private final BusinessOperationRepository operations;
    private final BookingOperationSyncService operationSync;

    public BookingTimeRepairStartupRunner(
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_BOOKING_ID:}") String bookingId,
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_EXPECTED_START_AT:}") String expectedStartAt,
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_TARGET_LOCAL_DATE:}") String targetLocalDate,
            @Value("${HELVOCA_BOOKING_TIME_REPAIR_TARGET_LOCAL_TIME:}") String targetLocalTime,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            BookingRepository bookings,
            BusinessRepository businesses,
            BusinessScheduleService schedule,
            BusinessOperationRepository operations,
            BookingOperationSyncService operationSync) {
        this.enabled = enabled;
        this.businessId = clean(businessId);
        this.bookingId = clean(bookingId);
        this.expectedStartAt = clean(expectedStartAt);
        this.targetLocalDate = clean(targetLocalDate);
        this.targetLocalTime = clean(targetLocalTime);
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.bookings = bookings;
        this.businesses = businesses;
        this.schedule = schedule;
        this.operations = operations;
        this.operationSync = operationSync;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId = uuid(businessId, "HELVOCA_BOOKING_TIME_REPAIR_BUSINESS_ID");
        UUID targetBookingId = uuid(bookingId, "HELVOCA_BOOKING_TIME_REPAIR_BOOKING_ID");

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            RepairResult result = tx.execute(status -> repair(
                    tenantId,
                    targetBookingId,
                    expectedStartAt,
                    targetLocalDate,
                    targetLocalTime));
            if (result == null) throw new IllegalStateException("Booking time repair returned no result");
            log.info(
                    "BOOKING_TIME_REPAIR status={} businessId={} bookingId={} previousStartAt={} targetStartAt={} timezone={}",
                    result.status(), tenantId, targetBookingId, result.previousStartAt(),
                    result.targetStartAt(), result.timezone());
        });
    }

    RepairResult repair(UUID businessId,
                        UUID bookingId,
                        String expectedStartAt,
                        String targetLocalDate,
                        String targetLocalTime) {
        Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new IllegalStateException("Booking time repair target was not found"));
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("Booking time repair business was not found"));

        ZoneId zone = ZoneId.of(business.getTimezone());
        Instant targetStartAt = ZonedDateTime.of(
                LocalDate.parse(targetLocalDate),
                LocalTime.parse(targetLocalTime),
                zone).toInstant();
        Instant currentStartAt = booking.getStartAt();

        if (targetStartAt.equals(currentStartAt)) {
            return new RepairResult("ALREADY_CORRECT", currentStartAt, targetStartAt, zone.getId());
        }

        Instant expected = Instant.parse(expectedStartAt);
        if (!expected.equals(currentStartAt)) {
            return new RepairResult("SKIPPED_UNEXPECTED_CURRENT_START", currentStartAt, targetStartAt, zone.getId());
        }
        if (!targetStartAt.isAfter(Instant.now())) {
            throw new IllegalStateException("Booking time repair target must be in the future");
        }

        Duration duration = Duration.between(booking.getStartAt(), booking.getEndAt());
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("Booking time repair found invalid booking duration");
        }
        Instant targetEndAt = targetStartAt.plus(duration);

        if (!schedule.isWithinBusinessHours(businessId, targetStartAt, targetEndAt)) {
            throw new IllegalStateException("Booking time repair target is outside business hours");
        }
        long overlaps = bookings.countOverlaps(
                businessId,
                booking.getServiceId(),
                targetStartAt,
                targetEndAt,
                BookingStatus.CANCELLED,
                bookingId);
        if (overlaps > 0) {
            throw new IllegalStateException("Booking time repair target slot is unavailable");
        }

        UUID operationId = booking.getOperationId();
        if (operationId == null) {
            throw new IllegalStateException("Booking time repair target has no operation");
        }
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalStateException("Booking time repair operation was not found"));

        booking.setStartAt(targetStartAt);
        booking.setEndAt(targetEndAt);
        bookings.saveAndFlush(booking);

        operationSync.synchronize(
                businessId,
                bookingId,
                operation.getSourceReferenceId(),
                operation.getSource(),
                "reschedule_booking");

        return new RepairResult("REPAIRED", currentStartAt, targetStartAt, zone.getId());
    }

    record RepairResult(String status, Instant previousStartAt, Instant targetStartAt, String timezone) {}

    private static UUID uuid(String value, String variable) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalStateException(variable + " must be a valid UUID", e);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
