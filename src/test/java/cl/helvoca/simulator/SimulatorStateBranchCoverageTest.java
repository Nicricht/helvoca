package cl.helvoca.simulator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulatorStateBranchCoverageTest {

    @Test
    void orderStateCoversMissingStaleInsufficientIdempotentAndLookupBranches() {
        SimulatorStateService state = new SimulatorStateService();
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        state.start(callId);

        var key = new SimulatorStateService.StockKey(businessId, itemId, null);
        var item = item(itemId, 2);

        assertEquals("ORDER_OPERATION_NOT_FOUND",
                state.confirmOrder(callId, UUID.randomUUID(), UUID.randomUUID(), Map.of()).errorCode());
        assertNull(state.orderById(callId, null));
        assertNull(state.orderById(callId, UUID.randomUUID()));
        assertTrue(state.orders(callId).isEmpty());

        var draft = state.createOrderDraft(
                callId, businessId, List.of(item), "PICKUP", null,
                bd(2000), BigDecimal.ZERO, bd(2000), "CLP");

        assertEquals("STALE_ORDER_CONFIRMATION",
                state.confirmOrder(callId, draft.operationId(), UUID.randomUUID(), Map.of(key, 2)).errorCode());
        assertEquals("INSUFFICIENT_STOCK",
                state.confirmOrder(callId, draft.operationId(), draft.confirmationToken(), Map.of(key, 1)).errorCode());

        var confirmed = state.confirmOrder(
                callId, draft.operationId(), draft.confirmationToken(), Map.of(key, 3));
        assertNull(confirmed.errorCode());
        assertFalse(confirmed.idempotentReplay());
        assertNotNull(confirmed.order().orderId());
        assertEquals(1, state.remainingAvailable(businessId, itemId, null, 3));
        assertEquals(confirmed.order(), state.orderById(callId, confirmed.order().orderId()));
        assertEquals(1, state.orders(callId).size());

        var replay = state.confirmOrder(
                callId, draft.operationId(), draft.confirmationToken(), Map.of(key, 3));
        assertNull(replay.errorCode());
        assertTrue(replay.idempotentReplay());

        assertNull(state.updateOrderDraft(
                callId, draft.operationId(), List.of(item), "PICKUP", null,
                bd(2000), BigDecimal.ZERO, bd(2000), "CLP"));
    }

    @Test
    void cancellationReleasesCommittedStockAndIsIdempotent() {
        SimulatorStateService state = new SimulatorStateService();
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        state.start(callId);

        var key = new SimulatorStateService.StockKey(businessId, itemId, null);
        var draft = state.createOrderDraft(
                callId, businessId, List.of(item(itemId, 1)), "PICKUP", null,
                bd(1000), BigDecimal.ZERO, bd(1000), "CLP");
        var confirmed = state.confirmOrder(
                callId, draft.operationId(), draft.confirmationToken(), Map.of(key, 2)).order();

        assertNull(state.cancelOrder(callId, UUID.randomUUID()));

        var cancelled = state.cancelOrder(callId, confirmed.orderId());
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(2, state.remainingAvailable(businessId, itemId, null, 2));

        var replay = state.cancelOrder(callId, confirmed.orderId());
        assertEquals(cancelled, replay);
    }

    @Test
    void finishHandlesUnknownSessionAndBothCommittedStockReleasePaths() {
        SimulatorStateService state = new SimulatorStateService();
        state.finish(UUID.randomUUID());

        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        var key = new SimulatorStateService.StockKey(businessId, itemId, null);

        UUID firstCall = UUID.randomUUID();
        UUID secondCall = UUID.randomUUID();
        state.start(firstCall);
        state.start(secondCall);

        var first = state.createOrderDraft(
                firstCall, businessId, List.of(item(itemId, 1)), "PICKUP", null,
                bd(1000), BigDecimal.ZERO, bd(1000), "CLP");
        var second = state.createOrderDraft(
                secondCall, businessId, List.of(item(itemId, 1)), "PICKUP", null,
                bd(1000), BigDecimal.ZERO, bd(1000), "CLP");

        assertNull(state.confirmOrder(
                firstCall, first.operationId(), first.confirmationToken(), Map.of(key, 3)).errorCode());
        assertNull(state.confirmOrder(
                secondCall, second.operationId(), second.confirmationToken(), Map.of(key, 3)).errorCode());
        assertEquals(1, state.remainingAvailable(businessId, itemId, null, 3));

        state.finish(firstCall);
        assertEquals(2, state.remainingAvailable(businessId, itemId, null, 3));

        state.finish(secondCall);
        assertEquals(3, state.remainingAvailable(businessId, itemId, null, 3));
    }

    @Test
    void promptContextAndDraftUpdateCoverCustomerBookingAndRevisionBranches() {
        SimulatorStateService state = new SimulatorStateService();
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        state.start(callId);

        String empty = state.promptContext(callId);
        assertTrue(empty.contains("Aún no hay cliente"));
        assertTrue(empty.contains("No hay reservas"));

        state.registerCustomer(callId, "Cliente Demo", null);
        UUID serviceId = UUID.randomUUID();
        var booking = state.createBooking(
                callId, serviceId, "Corte", java.time.Instant.parse("2030-01-01T12:00:00Z"),
                java.time.Instant.parse("2030-01-01T12:30:00Z"), "2030-01-01T09:00:00-03:00");
        assertTrue(state.promptContext(callId).contains("Cliente Demo"));
        assertTrue(state.promptContext(callId).contains("Corte"));
        state.cancelBooking(callId, booking.id());
        assertTrue(state.promptContext(callId).contains("No hay reservas"));

        var first = state.createOrderDraft(
                callId, businessId, List.of(item(itemId, 1)), "PICKUP", null,
                bd(1000), BigDecimal.ZERO, bd(1000), "CLP");
        var updated = state.updateOrderDraft(
                callId, first.operationId(), List.of(item(itemId, 2)), "PICKUP", null,
                bd(2000), BigDecimal.ZERO, bd(2000), "CLP");
        assertNotNull(updated);
        assertEquals(2, updated.revision());
        assertNotEquals(first.confirmationToken(), updated.confirmationToken());

        assertNull(state.updateOrderDraft(
                callId, UUID.randomUUID(), List.of(item(itemId, 1)), "PICKUP", null,
                bd(1000), BigDecimal.ZERO, bd(1000), "CLP"));
    }

    private static SimulatorStateService.SimulatedOrderItem item(UUID itemId, int quantity) {
        return new SimulatorStateService.SimulatedOrderItem(
                itemId, null, "Producto", quantity, bd(1000), bd(1000L * quantity), "CLP");
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
