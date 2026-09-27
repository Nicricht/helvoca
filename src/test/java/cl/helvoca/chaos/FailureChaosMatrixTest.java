package cl.helvoca.chaos;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FailureChaosMatrixTest {

    @Test
    void matrixHasUniqueScenariosAndCoversTheCriticalFailureFamilies() {
        var scenarios = FailureChaosMatrix.scenarios();
        assertTrue(scenarios.size() >= 6);

        Set<String> ids = new HashSet<>();
        Set<String> tests = new HashSet<>();
        for (var scenario : scenarios) {
            assertTrue(ids.add(scenario.id()), "duplicate chaos scenario: " + scenario.id());
            assertFalse(scenario.invariant().isBlank());
            assertFalse(scenario.tests().isEmpty());
            tests.addAll(scenario.tests());
        }

        assertTrue(tests.contains("SafeOperationRetryChaosCertificationTest"));
        assertTrue(tests.contains("PaymentWebhookChaosCertificationTest"));
        assertTrue(tests.contains("MetaWhatsAppInboundJobServiceTest"));
        assertTrue(tests.contains("PersistentJobStoreIntegrationTest"));
        assertTrue(tests.contains("DeepgramAudioTranscriptionProviderTest"));
        assertTrue(tests.contains("ConversationReplayFixtureSuiteTest"));
    }
}
