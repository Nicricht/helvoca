package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstFictionalCustomerWeekGateContractTest {

    @Test
    void fictionalCustomerWeekGateCoversTheOperationalWeekAndSafetyBoundary() throws Exception {
        Path gate = Path.of("scripts/ci/first-fictional-customer-week.sh");
        assertTrue(Files.exists(gate), "first fictional customer week gate must exist");

        String script = Files.readString(gate);

        for (String required : new String[] {
                "DevDataInitializerTest",
                "DemoConversationScenariosTest",
                "SimulatorToolExecutorTest",
                "RealtimeToolServiceTest",
                "GoldenJourneyCommercialV1IntegrationTest",
                "BookingConversationCertificationPackTest",
                "ClosingConversationCertificationPackTest",
                "RealtimeHumanTransferToolTest",
                "SafeOperationRetryChaosCertificationTest",
                "ConversationQualityGoldenScenarioTest",
                "PostgresRowLevelSecurityIntegrationTest",
                "PilotMetricsServiceTest",
                "CustomerCommercialTimelineServiceTest",
                "CommercialEntitlementServiceTest",
                "JourneyTraceServiceIntegrationTest",
                "ConversationReplayFixtureSuiteTest"
        }) {
            assertTrue(script.contains(required), "week gate must include " + required);
        }

        for (String scenario : new String[] {
                "onboarding-and-business-configuration",
                "price-and-faq-questions",
                "availability-book-reschedule-cancel",
                "duplicates-and-repeated-conversation",
                "closed-hours-and-missing-service",
                "change-of-mind-and-interruption",
                "human-handoff",
                "tool-error-and-bounded-retry",
                "multi-tenant-isolation",
                "metrics-timeline-entitlements-dashboard",
                "replay-and-observability"
        }) {
            assertTrue(script.contains(scenario), "week gate must name scenario " + scenario);
        }

        assertTrue(script.contains("e2e/frontend-ux.spec.js"),
                "week gate must keep first-user registration and login UX in certification");
        assertTrue(script.contains("e2e/react-settings-core.spec.js"),
                "week gate must keep onboarding and settings UX in certification");
        assertTrue(script.contains("e2e/react-home.spec.js"),
                "week gate must certify the owner operational dashboard");
        for (String file : new String[] {
                "e2e/frontend-ux.spec.js",
                "e2e/react-settings-core.spec.js",
                "e2e/react-home.spec.js"
        }) {
            assertTrue(Files.isRegularFile(Path.of(file)), "certification spec must exist: " + file);
        }
        assertTrue(script.contains("no real calls"),
                "week gate must forbid real calls");
        assertTrue(script.contains("no real WhatsApp"),
                "week gate must forbid real WhatsApp");
        assertTrue(script.contains("no real payments"),
                "week gate must forbid real payments");
        assertTrue(script.contains("no production mutation"),
                "week gate must forbid production mutation");
        assertTrue(script.contains("FIRST FICTIONAL CUSTOMER WEEK: PASS"),
                "week gate must expose an unambiguous PASS marker");
    }
}
