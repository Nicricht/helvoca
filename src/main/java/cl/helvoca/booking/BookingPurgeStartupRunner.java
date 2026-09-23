package cl.helvoca.booking;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Component
public class BookingPurgeStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BookingPurgeStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;
    private final BookingRepository bookings;

    public BookingPurgeStartupRunner(
            @Value("${HELVOCA_BOOKING_PURGE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_BOOKING_PURGE_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            PlatformTransactionManager transactionManager,
            BookingRepository bookings) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
        this.bookings = bookings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            throw new IllegalStateException("HELVOCA_BOOKING_PURGE_BUSINESS_ID must be a valid UUID", e);
        }

        databaseContext.runAsTenant(tenantId, () -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            PurgeResult result = tx.execute(status -> purge(tenantId));
            if (result == null) {
                throw new IllegalStateException("Booking purge transaction returned no result");
            }
            log.warn("BOOKING_PURGE_COMPLETE businessId={} deleted={} remaining={}",
                    tenantId, result.deleted(), result.remaining());
        });
    }

    PurgeResult purge(UUID businessId) {
        List<Booking> existing = bookings.findAllByBusinessIdOrderByStartAtDesc(businessId);
        int deleted = existing.size();
        if (!existing.isEmpty()) {
            bookings.deleteAllInBatch(existing);
            bookings.flush();
        }

        int remaining = bookings.findAllByBusinessIdOrderByStartAtDesc(businessId).size();
        if (remaining != 0) {
            throw new IllegalStateException("Booking purge incomplete; remaining=" + remaining);
        }
        return new PurgeResult(deleted, remaining);
    }

    record PurgeResult(int deleted, int remaining) {}
}
