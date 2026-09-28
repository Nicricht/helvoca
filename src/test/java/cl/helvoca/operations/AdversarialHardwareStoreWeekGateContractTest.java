package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdversarialHardwareStoreWeekGateContractTest {

    @Test
    void adversarialHardwareStoreWeekGateCoversSevenDaysAndCriticalSafetyRules() throws Exception {
        Path gate = Path.of("scripts/ci/adversarial-hardware-store-week.sh");
        assertTrue(Files.exists(gate), "adversarial hardware store week gate must exist");

        String script = Files.readString(gate);

        for (String required : new String[] {
                "AdversarialHardwareStoreFixtureTest",
                "AdversarialHardwareStoreWeekCertificationTest",
                "CommercialOperationToolServiceTest",
                "ConfirmationAwareCommercialOperationToolServiceTest",
                "OrderWorkflowServiceTest",
                "InventoryServiceTest",
                "InventoryVariantServiceTest",
                "GoldenJourneyCommercialV1IntegrationTest",
                "OmnichannelCommerceJourneyIntegrationTest",
                "SafeOperationRetryChaosCertificationTest",
                "PostgresRowLevelSecurityIntegrationTest",
                "JourneyTraceServiceIntegrationTest",
                "ConversationReplayFixtureSuiteTest"
        }) {
            assertTrue(script.contains(required), "week gate must include " + required);
        }

        for (String scenario : new String[] {
                "day-1:catalog-price-unit-similar-products",
                "day-2:inventory-out-of-stock-low-stock-no-substitution",
                "day-3:quotes-intent-corrections-duplicate-confirmation",
                "day-4:orders-pickup-delivery-coverage",
                "day-5:non-technical-unknown-handoff-ambiguity",
                "day-6:tool-failure-bounded-retry-concurrency-tenant-isolation",
                "day-7:long-conversations-mixed-problems-full-regression"
        }) {
            assertTrue(script.contains(scenario), "week gate must name scenario " + scenario);
        }

        assertTrue(script.contains("180 simulated conversations"),
                "week gate must retain the large adversarial sample");
        assertTrue(script.contains("no real calls"), "week gate must forbid real calls");
        assertTrue(script.contains("no real WhatsApp"), "week gate must forbid real WhatsApp");
        assertTrue(script.contains("no real payments"), "week gate must forbid real payments");
        assertTrue(script.contains("no live credentials"), "week gate must forbid live credentials");
        assertTrue(script.contains("no real delivery"), "week gate must forbid real delivery");
        assertTrue(script.contains("no production mutation"), "week gate must forbid production mutation");
        assertTrue(script.contains("ADVERSARIAL HARDWARE STORE WEEK: PASS"),
                "week gate must expose the exact PASS marker");
        assertTrue(script.contains("ADVERSARIAL HARDWARE STORE WEEK: FAIL"),
                "week gate must expose the exact FAIL marker");
    }
}
