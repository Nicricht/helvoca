package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private InventoryStockRepository stocks;
    private InventoryReservationRepository reservations;
    private InventoryMovementRepository movements;
    private CatalogItemRepository catalog;
    private TenantProvider tenant;
    private InventoryService service;

    private UUID businessId;
    private UUID productId;
    private CatalogItem product;

    @BeforeEach
    void setUp() {
        stocks = mock(InventoryStockRepository.class);
        reservations = mock(InventoryReservationRepository.class);
        movements = mock(InventoryMovementRepository.class);
        catalog = mock(CatalogItemRepository.class);
        tenant = mock(TenantProvider.class);
        service = new InventoryService(stocks, reservations, movements, catalog, tenant);

        businessId = UUID.randomUUID();
        productId = UUID.randomUUID();

        product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Shampoo");
        product.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(stocks.saveAndFlush(any(InventoryStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.saveAndFlush(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movements.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reserveLocksAvailableStockAndWritesMovement() {
        InventoryStock stock = stock(5, 1, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));

        InventoryService.ReservationView view = service.reserve(
                productId,
                new InventoryService.ReservationInput(3, "ORDER", UUID.randomUUID(), null, "checkout")
        );

        assertEquals(4, stock.getReserved());
        assertEquals(1, stock.available());
        assertEquals(InventoryReservation.Status.ACTIVE, view.status());
        assertEquals(3, view.quantity());

        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(movement.capture());
        assertEquals(InventoryMovement.Type.RESERVATION, movement.getValue().getType());
        assertEquals(0, movement.getValue().getQuantityDelta());
        assertEquals(3, movement.getValue().getReservedDelta());
        assertEquals(5, movement.getValue().getOnHandAfter());
        assertEquals(4, movement.getValue().getReservedAfter());
    }

    @Test
    void reserveRejectsOverselling() {
        InventoryStock stock = stock(3, 2, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));

        assertThrows(ConflictException.class, () -> service.reserve(
                productId,
                new InventoryService.ReservationInput(2, "ORDER", UUID.randomUUID(), null, null)
        ));

        verify(reservations, never()).saveAndFlush(any());
        verify(movements, never()).save(any());
    }

    @Test
    void consumeDecrementsOnHandAndReservedExactlyOnce() {
        UUID reservationId = UUID.randomUUID();
        InventoryStock stock = stock(5, 2, true);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(reservationId);
        reservation.setBusinessId(businessId);
        reservation.setCatalogItemId(productId);
        reservation.setQuantity(2);
        reservation.setStatus(InventoryReservation.Status.ACTIVE);

        when(reservations.lockByIdAndBusinessId(reservationId, businessId)).thenReturn(Optional.of(reservation));
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));

        InventoryService.ReservationView first = service.consume(reservationId, "payment approved");

        assertEquals(3, stock.getOnHand());
        assertEquals(0, stock.getReserved());
        assertEquals(InventoryReservation.Status.CONSUMED, first.status());

        InventoryService.ReservationView replay = service.consume(reservationId, "webhook replay");
        assertEquals(3, stock.getOnHand());
        assertEquals(0, stock.getReserved());
        assertEquals(InventoryReservation.Status.CONSUMED, replay.status());

        verify(movements, times(1)).save(any());
    }

    @Test
    void adjustmentCannotReduceOnHandBelowReserved() {
        InventoryStock stock = stock(3, 2, true);
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.of(stock));

        assertThrows(ConflictException.class, () -> service.adjust(
                productId,
                new InventoryService.AdjustmentInput(-2, "MANUAL", null, "count correction")
        ));

        assertEquals(3, stock.getOnHand());
        verify(movements, never()).save(any());
    }

    @Test
    void trackedStockCanBeConfiguredWithSkuAndThreshold() {
        when(stocks.findByBusinessIdAndSkuIgnoreCase(businessId, "SHAMPOO-01")).thenReturn(Optional.empty());
        when(stocks.lockByBusinessAndCatalogItem(businessId, productId)).thenReturn(Optional.empty());

        InventoryService.StockView view = service.configure(
                productId,
                new InventoryService.ConfigureInput(" shampoo-01 ", true, 10, 2, "initial stock")
        );

        assertEquals("SHAMPOO-01", view.sku());
        assertEquals(10, view.onHand());
        assertEquals(10, view.available());
        assertEquals(2, view.reorderThreshold());
        assertFalse(view.lowStock());
        verify(movements).save(any(InventoryMovement.class));
    }

    private InventoryStock stock(int onHand, int reserved, boolean tracking) {
        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(businessId);
        stock.setCatalogItemId(productId);
        stock.setTrackingEnabled(tracking);
        stock.setOnHand(onHand);
        stock.setReserved(reserved);
        stock.setReorderThreshold(1);
        return stock;
    }
}
