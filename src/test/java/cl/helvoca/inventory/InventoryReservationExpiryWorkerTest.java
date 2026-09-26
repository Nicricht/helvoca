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
}
