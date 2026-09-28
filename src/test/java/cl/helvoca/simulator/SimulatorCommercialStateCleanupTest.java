package cl.helvoca.simulator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulatorCommercialStateCleanupTest {

    @Test
    void finishingSimulationReleasesItsEphemeralCommittedStock() {
        SimulatorStateService state = new SimulatorStateService();
        UUID callId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        state.start(callId);
        var item = new SimulatorStateService.SimulatedOrderItem(
                itemId, null, "Última unidad", 1,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1000), "CLP");
        var draft = state.createOrderDraft(
                callId, businessId, List.of(item), "PICKUP", null,
                BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.valueOf(1000), "CLP");

        var key = new SimulatorStateService.StockKey(businessId, itemId, null);
        var confirmation = state.confirmOrder(
                callId, draft.operationId(), draft.confirmationToken(), Map.of(key, 1));

        assertNull(confirmation.errorCode());
        assertEquals(0, state.remainingAvailable(businessId, itemId, null, 1));

        state.finish(callId);

        assertEquals(1, state.remainingAvailable(businessId, itemId, null, 1),
                "A finished simulation must not contaminate later sessions.");
    }
}
