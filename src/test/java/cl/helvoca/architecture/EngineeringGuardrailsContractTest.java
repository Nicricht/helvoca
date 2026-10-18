package cl.helvoca.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EngineeringGuardrailsContractTest {

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void repositoryGuardrailsRequireFirstPassEngineeringAndFreshEvidence() throws Exception {
        String agents = read("AGENTS.md");
        assertTrue(agents.contains("First-Pass Engineering"));
        assertTrue(agents.contains("software-factory/skill/first-pass-engineering/SKILL.md"));
        assertTrue(agents.contains("100% differential line, branch and method coverage"));
        assertTrue(agents.contains("100% mapped interaction/state-transition coverage"));
        assertTrue(agents.contains("100% mapped real-PostgreSQL invariant coverage"));
        assertTrue(agents.toLowerCase().contains("final commit"));
        assertTrue(agents.contains("Never develop directly on `main`"));
        assertTrue(agents.toLowerCase().contains("regression tests for every bug fixed"));
    }

    @Test
    void freshChatCannotSilentlyDropEvidenceFirstAntiDuplicationContract() throws Exception {
        String agents = read("AGENTS.md");
        String fastGateContract = read("scripts/ci/check-qa-governance.py");
        assertTrue(agents.contains("MANDATORY ANTI-REDUNDANCY AUDIT BEFORE STATUS OR NEXT WORK"));
        assertTrue(agents.contains("docs/PLAN_MAESTRO_CIERRE_COMERCIAL.md"));
        assertTrue(agents.contains("Issue #823"));
        assertTrue(agents.contains("DELIVERED_AND_VERIFIED"));
        assertTrue(agents.contains("DONE_OFFLINE_NOT_DEPLOYED"));
        assertTrue(agents.contains("EXTERNAL_ACCEPTANCE_PENDING"));
        assertTrue(agents.contains("NOT_VERIFIED"));
        assertTrue(agents.contains("verifiable evidence-delta"));
        assertTrue(agents.contains("Do not re-run historical success"));
        assertTrue(agents.contains("Do not promote optional improvements to mandatory V1 blockers"));
        assertTrue(agents.contains("PILOTO_ASISTIDO_VENDIBLE"));
        assertTrue(agents.contains("ÚNICAS BRECHAS REALES DE ACEPTACIÓN"));
        assertTrue(agents.contains("what remains"));
        assertTrue(fastGateContract.contains("mandatory anti-redundancy audit before status or next work"));
        assertTrue(fastGateContract.contains("do not promote optional improvements to mandatory v1 blockers"));
    }

    @Test
    void newChatMustEvidenceFreshRemoteHeadAndPriorCompletedWorkBeforeWrites() throws Exception {
        String agents = read("AGENTS.md");
        String template = read(".github/pull_request_template.md");
        String completion = read("docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md");
        String guard = read("scripts/ci/check-qa-governance.py");

        assertTrue(agents.contains("PRE-CHANGE FRESH-CONTEXT WRITE GATE"));
        assertTrue(agents.contains("FRESH_CONTEXT_EVIDENCE_RECEIPT"));
        assertTrue(agents.contains("latest remote HEAD"));
        assertTrue(agents.contains("changed since the last checkpoint"));
        assertTrue(agents.contains("Do not edit if the gap is already closed"));
        assertTrue(agents.contains("docs/engineering/QA_POLICY.md"));
        assertTrue(agents.contains("docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md"));

        assertTrue(template.contains("PRE-CHANGE FRESH-CONTEXT WRITE GATE"));
        assertTrue(template.contains("FRESH_CONTEXT_EVIDENCE_RECEIPT"));
        assertTrue(template.contains("changes since the last checkpoint"));
        assertTrue(completion.contains("PRE-CHANGE FRESH-CONTEXT WRITE GATE"));
        assertTrue(completion.contains("FRESH_CONTEXT_EVIDENCE_RECEIPT"));
        assertTrue(guard.contains("pre-change fresh-context write gate"));
        assertTrue(guard.contains("fresh_context_evidence_receipt"));
    }

    @Test
    void freshContextHasLiveGitHubApiGateNotOnlyMarkdownWordChecks() throws Exception {
        String workflow = read(".github/workflows/ci.yml");
        String script = read("scripts/ci/verify_prechange_receipt.py");
        String unitTests = read("scripts/ci/test_verify_prechange_receipt.py");
        assertTrue(workflow.contains("Verify pre-change evidence receipt against GitHub"));
        assertTrue(workflow.contains("python3 scripts/ci/verify_prechange_receipt.py"));
        assertTrue(workflow.contains("coverage report --fail-under=100"));
        assertTrue(workflow.contains("issues: read"));
        assertTrue(workflow.contains("pull-requests: read"));
        assertTrue(script.contains("FRESH_CONTEXT_EVIDENCE_RECEIPT_V2"));
        assertTrue(script.contains("author_association"));
        assertTrue(script.contains("No trusted pre-change receipt"));
        assertTrue(unitTests.contains("test_comment_after_commit_is_invalid"));
        assertTrue(unitTests.contains("test_overlong_pagination_fails_closed"));
        assertTrue(unitTests.contains("test_changed_head_fails_closed"));
    }

    @Test
    void fastGateRunsSoftwareFactoryContractTestsWhenFactoryChanges() throws Exception {
        String fastGate = read("scripts/ci/fast-gate.sh");
        assertTrue(fastGate.contains("python3 -m unittest discover -s software-factory/tests -p 'test_*.py' -v"));
    }

    @Test
    void protectedTestCheckAggregatesSpecializedFullGateAndRejectsSkippedPrerequisites() throws Exception {
        String workflow = read(".github/workflows/ci.yml");
        assertTrue(workflow.contains("needs: [fast-gate, golden-journey-release-contract, system-integration-e2e, home-frontend-coverage, inventory-frontend-coverage, legacy-v99-runtime-on-v103]"));
        assertTrue(workflow.contains("if: always() && (github.event_name == 'pull_request'"));
        assertTrue(workflow.contains("Enforce applicable upstream Full Gate results"));
        assertTrue(workflow.contains("if [ \"$2\" != \"success\" ]; then"));
        assertTrue(workflow.contains("required_gate fast-gate \"$FAST_GATE_RESULT\""));
        assertTrue(workflow.contains("required_gate golden-journey-release-contract \"$GOLDEN_JOURNEY_RESULT\""));
        assertTrue(workflow.contains("required_gate system-integration-e2e \"$SYSTEM_E2E_RESULT\""));
        assertTrue(workflow.contains("if [ \"$EVENT_NAME\" = \"pull_request\" ]; then"));
        assertTrue(workflow.contains("required_gate home-frontend-coverage \"$HOME_COVERAGE_RESULT\""));
        assertTrue(workflow.contains("required_gate inventory-frontend-coverage \"$INVENTORY_COVERAGE_RESULT\""));
        assertTrue(workflow.contains("required_gate legacy-v99-runtime-on-v103 \"$LEGACY_ROLLBACK_RESULT\""));
    }

    @Test
    void projectInvariantsProtectCriticalCommercialAndTenantTruths() throws Exception {
        String invariants = read("docs/engineering/invariants.md").toLowerCase();
        assertTrue(invariants.contains("tenant"));
        assertTrue(invariants.contains("simulator"));
        assertTrue(invariants.contains("external side effect"));
        assertTrue(invariants.contains("payment"));
        assertTrue(invariants.contains("duplicate"));
        assertTrue(invariants.contains("order"));
    }
}
