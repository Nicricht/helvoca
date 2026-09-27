package cl.helvoca.inventory;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryReservationExpiryWorkerTest {
    @Test
    void pollDiscoversExpiredReservationsAsSystemAndProcessesThemAsTenant() {
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        TenantDatabaseContext databaseContext = new TenantDatabaseContext();
        InventoryReservationExpiryWorker worker =
                new InventoryReservationExpiryWorker(true, databaseContext, reservations, inventory);

        UUID businessId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(reservationId);
        reservation.setBusinessId(businessId);
        reservation.setStatus(InventoryReservation.Status.ACTIVE);
        reservation.setExpiresAt(Instant.now().minusSeconds(60));

        when(reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(InventoryReservation.Status.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(reservation));
        when(inventory.expireReservationForBusiness(
                eq(businessId), eq(reservationId), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.EXPIRED);

        worker.poll();

        verify(inventory).expireReservationForBusiness(
                eq(businessId), eq(reservationId), any(Instant.class));
    }

    @Test
    void disabledWorkerDoesNothing() {
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        InventoryReservationExpiryWorker worker =
                new InventoryReservationExpiryWorker(
                        false, new TenantDatabaseContext(), reservations, inventory);

        worker.poll();

        verifyNoInteractions(reservations, inventory);
    }
    @Test
    void pollCoversAllExpiryOutcomesAndContinuesAfterOneFailure() {
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        InventoryReservationExpiryWorker worker =
                new InventoryReservationExpiryWorker(
                        true, new TenantDatabaseContext(), reservations, inventory);

        UUID businessId = UUID.randomUUID();
        InventoryReservation expired = candidate(businessId);
        InventoryReservation recovered = candidate(businessId);
        InventoryReservation pending = candidate(businessId);
        InventoryReservation unavailable = candidate(businessId);
        InventoryReservation noop = candidate(businessId);
        InventoryReservation failing = candidate(businessId);

        when(reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(InventoryReservation.Status.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(expired, recovered, pending, unavailable, noop, failing));

        when(inventory.expireReservationForBusiness(eq(businessId), eq(expired.getId()), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.EXPIRED);
        when(inventory.expireReservationForBusiness(eq(businessId), eq(recovered.getId()), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.PAID_RECOVERED);
        when(inventory.expireReservationForBusiness(eq(businessId), eq(pending.getId()), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.PAYMENT_PENDING);
        when(inventory.expireReservationForBusiness(eq(businessId), eq(unavailable.getId()), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.PAYMENT_STATE_UNAVAILABLE);
        when(inventory.expireReservationForBusiness(eq(businessId), eq(noop.getId()), any(Instant.class)))
                .thenReturn(InventoryService.ExpiryResult.NOOP);
        when(inventory.expireReservationForBusiness(eq(businessId), eq(failing.getId()), any(Instant.class)))
                .thenThrow(new IllegalStateException("simulated reconciliation failure"));

        worker.poll();

        verify(inventory, times(6)).expireReservationForBusiness(
                eq(businessId), any(UUID.class), any(Instant.class));
    }

    @Test
    void enabledWorkerWithNoCandidatesDoesNotInvokeInventory() {
        InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        InventoryReservationExpiryWorker worker =
                new InventoryReservationExpiryWorker(
                        true, new TenantDatabaseContext(), reservations, inventory);

        when(reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(InventoryReservation.Status.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        worker.poll();

        verifyNoInteractions(inventory);
    }

    private static InventoryReservation candidate(UUID businessId) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setBusinessId(businessId);
        reservation.setStatus(InventoryReservation.Status.ACTIVE);
        reservation.setExpiresAt(Instant.now().minusSeconds(60));
        return reservation;
    }

}
