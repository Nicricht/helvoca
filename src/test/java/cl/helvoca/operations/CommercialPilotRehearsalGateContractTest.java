package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommercialPilotRehearsalGateContractTest {

    @Test
    void rehearsalGateEnumeratesCriticalCommercialContractsAndSafetyBoundary() throws Exception {
        Path gate = Path.of("scripts/ci/commercial-pilot-rehearsal.sh");
        assertTrue(Files.exists(gate), "commercial pilot rehearsal gate must exist");

        String script = Files.readString(gate);

        for (String required : new String[] {
                "DevDataInitializerTest",
                "GoldenJourneyCommercialV1IntegrationTest",
                "PilotGoNoGoServiceTest",
                "BillingSubscriptionServiceTest",
                "CommercialEntitlementServiceTest",
                "MercadoPagoWebhookControllerTest",
                "PostgresRowLevelSecurityIntegrationTest",
                "JourneyTraceServiceIntegrationTest",
                "ConversationReplayFixtureSuiteTest",
                "TwilioVoiceTransportSessionTest",
                "ClosingConversationCertificationPackTest",
                "BookingConversationCertificationPackTest",
                "PilotMetricsServiceTest"
        }) {
            assertTrue(script.contains(required), "gate must include " + required);
        }

        assertTrue(script.contains("pilot-e2e-certification.sh"),
                "gate must execute the existing Pilot E2E certification");
        assertTrue(script.contains("no real calls"),
                "gate must state that real calls are forbidden");
        assertTrue(script.contains("no real payments"),
                "gate must state that real payments are forbidden");
        assertTrue(script.contains("PILOT REHEARSAL: PASS"),
                "gate must expose an unambiguous PASS marker");
    }
}
