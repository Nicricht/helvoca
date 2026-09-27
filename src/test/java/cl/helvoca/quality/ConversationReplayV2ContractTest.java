package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConversationReplayV2ContractTest {

    @Test
    void replayV2ProvidesFixtureAnonymizerAndPersistedCallCaptureService() {
        assertDoesNotThrow(() -> Class.forName("cl.helvoca.quality.ConversationReplayFixture"));
        assertDoesNotThrow(() -> Class.forName("cl.helvoca.quality.ConversationReplayAnonymizer"));
        assertDoesNotThrow(() -> Class.forName("cl.helvoca.quality.ConversationReplayCaptureService"));
    }
}
