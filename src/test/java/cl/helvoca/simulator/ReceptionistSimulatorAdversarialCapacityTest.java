package cl.helvoca.simulator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceptionistSimulatorAdversarialCapacityTest {

    @Test
    void simulatorSupportsTheThirtyTurnAdversarialConversationWindow() throws Exception {
        Field maxTurns = ReceptionistSimulatorService.class.getDeclaredField("MAX_TURNS");
        maxTurns.setAccessible(true);
        int configured = maxTurns.getInt(null);

        assertTrue(configured >= 30,
                "Manual adversarial certification requires conversations of up to 30 user turns; configured=" + configured);
    }
}
