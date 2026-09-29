package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialReleaseReadinessGateContractTest {

    @Test
    void commercialReadinessGateKeepsLaunchFailClosedAndExternalDependenciesExplicit() throws Exception {
        Path gate = Path.of("scripts/ci/commercial-release-readiness-first-business.sh");
        assertTrue(Files.exists(gate), "commercial readiness gate must exist");

        String script = Files.readString(gate);

        for (String required : new String[] {
                "PilotLaunchControlServiceTest",
                "ControlledPilotExternalEffectGuardTest",
                "PilotGoNoGoServiceTest",
                "PostgresRowLevelSecurityIntegrationTest",
                "InventoryPostgresConcurrencyIntegrationTest",
                "BillingSubscriptionServiceTest",
                "SaasBillingSandboxCertificationStartupRunnerTest",
                "CommercialEntitlementServiceTest",
                "ReconciliationServiceTest",
                "JourneyTraceServiceIntegrationTest",
                "commercial-pilot-rehearsal.sh",
                "pilot-preflight.spec.js",
                "frontend-release-candidate.spec.js",
                "frontend-release-candidate-hardening.spec.js"
        }) {
            assertTrue(script.contains(required), "readiness gate must include " + required);
        }

        assertTrue(script.contains("git merge-base --is-ancestor"),
                "candidate must prove current main is an ancestor");
        assertTrue(script.contains("no deploy, real calls, real WhatsApp, real payments, provisioning or production mutation"),
                "gate must keep real external effects outside certification");
        assertTrue(script.contains("COMMERCIAL RELEASE READINESS FIRST BUSINESS: PASS"),
                "gate must expose an unambiguous PASS marker");

        String readiness = Files.readString(Path.of(
                "docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md"));
        assertTrue(readiness.contains("PROVIDER TEST ROUND-TRIP PENDING"),
                "Mercado Pago provider round trip must remain an explicit external gate");
        assertTrue(readiness.contains("CUSTOMER-SPECIFIC ACTIVATION PENDING"),
                "first customer activation must remain tenant-specific");
        assertTrue(readiness.contains("Real-customer activation: NOT AUTHORIZED"),
                "green software must not auto-authorize a real customer");
        assertTrue(readiness.contains("readiness evidence only"),
                "GO must not be treated as provider activation");

        String external = Files.readString(Path.of("docs/COMMERCIAL_EXTERNAL_GATES_V1.md"));
        assertTrue(external.contains("PROVIDER ROUND-TRIP PENDING"),
                "external gate document must keep Mercado Pago pending");
        assertTrue(external.contains("CUSTOMER-SPECIFIC DATA PENDING"),
                "external gate document must keep customer-specific data pending");
        assertFalse(external.contains("Status: **COMPLETE / PROVIDER"),
                "Mercado Pago must not be silently marked complete");
    }
}
