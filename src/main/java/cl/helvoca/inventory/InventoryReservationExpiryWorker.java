package cl.helvoca.inventory;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryReservationExpiryWorker {
    private static final Logger log =
            LoggerFactory.getLogger(InventoryReservationExpiryWorker.class);

    private final boolean enabled;
    private final TenantDatabaseContext databaseContext;
    private final InventoryReservationRepository reservations;
    private final InventoryService inventory;

    public InventoryReservationExpiryWorker(
            @Value("${HELVOCA_INVENTORY_RESERVATION_EXPIRY_ENABLED:true}") boolean enabled,
            TenantDatabaseContext databaseContext,
            InventoryReservationRepository reservations,
            InventoryService inventory) {
        this.enabled = enabled;
        this.databaseContext = databaseContext;
        this.reservations = reservations;
        this.inventory = inventory;
    }

    @Scheduled(
            initialDelayString = "${HELVOCA_INVENTORY_RESERVATION_EXPIRY_INITIAL_DELAY_MS:15000}",
            fixedDelayString = "${HELVOCA_INVENTORY_RESERVATION_EXPIRY_DELAY_MS:30000}")
    public void poll() {
        if (!enabled) return;

        Instant now = Instant.now();
        List<Candidate> candidates = databaseContext.callAsSystem(() ->
                reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                                InventoryReservation.Status.ACTIVE, now)
                        .stream()
                        .map(value -> new Candidate(value.getBusinessId(), value.getId()))
                        .toList());

        int expired = 0;
        int paidRecovered = 0;
        int paymentPending = 0;

        for (Candidate candidate : candidates) {
            try {
                InventoryService.ExpiryResult result = databaseContext.callAsTenant(
                        candidate.businessId(),
                        () -> inventory.expireReservationForBusiness(
                                candidate.businessId(), candidate.reservationId(), now));
                switch (result) {
                    case EXPIRED -> expired++;
                    case PAID_RECOVERED -> paidRecovered++;
                    case PAYMENT_PENDING, PAYMENT_STATE_UNAVAILABLE -> paymentPending++;
                    case NOOP -> { }
                }
            } catch (RuntimeException e) {
                log.warn(
                        "INVENTORY_RESERVATION_EXPIRY_FAILED businessId={} reservationId={} error={}",
                        candidate.businessId(),
                        candidate.reservationId(),
                        e.getClass().getSimpleName());
            }
        }

        if (!candidates.isEmpty()) {
            log.info(
                    "INVENTORY_RESERVATION_EXPIRY scanned={} expired={} paidRecovered={} paymentProtected={}",
                    candidates.size(), expired, paidRecovered, paymentPending);
        }
    }

    private record Candidate(UUID businessId, UUID reservationId) {}
}
