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

        assertTrue(script.contains("api.github.com/repos"),
                "candidate must prove ancestry through GitHub compare");
        assertTrue(script.contains("behind_by"),
                "candidate must fail if it is behind audited main");
        assertTrue(script.contains("no deploy, real calls, real WhatsApp, real payments, provisioning or production mutation"),
                "gate must keep real external effects outside certification");
        assertTrue(script.contains("COMMERCIAL RELEASE READINESS FIRST BUSINESS: PASS"),
                "gate must expose an unambiguous PASS marker");

        String workflow = Files.readString(Path.of(
                ".github/workflows/commercial-release-readiness-safe-ci.yml"));
        assertTrue(workflow.contains("cert/commercial-readiness-*"),
                "commercial readiness CI must be reusable from dedicated certification branches");
        assertFalse(workflow.contains("8dc165d102a4157fdc6e3cd38b51b76b70b388d1"),
                "commercial readiness CI must not pin an obsolete main SHA");
        assertFalse(workflow.contains("dbba4d7260c96763c13a0c56d4c1cb108d4986ca"),
                "differential coverage must not pin an obsolete RC SHA");
        assertTrue(workflow.contains("git rev-parse refs/remotes/origin/main"),
                "commercial readiness CI must derive differential coverage from current main");

        String readiness = Files.readString(Path.of(
                "docs/COMMERCIAL_RELEASE_READINESS_FIRST_BUSINESS_V1.md"));
        assertTrue(readiness.contains("PROVIDER TEST ROUND-TRIP PENDING"),
                "Mercado Pago provider round trip must remain an explicit external gate");
        assertTrue(readiness.contains("CUSTOMER-SPECIFIC ACTIVATION PENDING"),
                "first customer activation must remain tenant-specific");
        assertTrue(readiness.contains("**Real-customer activation:** NOT AUTHORIZED"),
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
