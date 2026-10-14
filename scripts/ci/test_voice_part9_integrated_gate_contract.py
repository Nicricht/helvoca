"""Part 9 workflow guard mutations: reject silent downgrades and effects."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "part9_contract", ROOT / "scripts/ci/voice_part9_integrated_gate_contract.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class Part9IntegratedNoGoWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = guard.WORKFLOW.read_text(encoding="utf-8")

    def blocked(self, content: object, code: str):
        result = guard.assess(content)
        self.assertEqual("BLOCKED", result["definition"], result)
        self.assertIn(code, result["blockers"], result)
        self.assertEqual("NO-GO", result["prod_deploy"])
        self.assertFalse(result["may_merge"])
        self.assertFalse(result["may_call"])

    def test_original_has_eight_gates_but_never_claims_execution(self):
        report = guard.assess(self.source)
        self.assertEqual("PASS_OFFLINE_DEFINITION", report["definition"], report)
        self.assertEqual(8, report["expected_jobs"])
        self.assertEqual("NOT_PROVEN_BY_STATIC_AUDITOR", report["executed_ci"])
        self.assertFalse(report["commercial_go"])

    def test_invalid_source_blocks(self):
        for x in (None, {}, 1, "", "on: push"):
            with self.subTest(x=x):
                self.blocked(x, "JOBS_MISSING" if isinstance(x, str)
                             else "INVALID_OR_MISSING_WORKFLOW")

    def test_main_and_manual_triggers_rejected(self):
        self.blocked(
            self.source.replace(
                "test/voice-release-part9-full-regression-offline-*",
                "main", 1),
            "STACKED_BRANCH_TRIGGER_ONLY")
        self.blocked(self.source + "\nworkflow_dispatch:\n",
                     "FORBIDDEN_PRODUCTION_OPERATION_OR_SECRET")

    def test_missing_or_extra_gates_rejected(self):
        self.blocked(
            self.source.replace("  part9-cross-tenant-voice-adversarial:",
                                "  unauthorized-new-job:", 1),
            "EIGHT_GATES_EXACTLY_REQUIRED")
        self.blocked(
            self.source + "\n  accidental-production-smoke:\n    runs-on: ubuntu-latest\n",
            "EIGHT_GATES_EXACTLY_REQUIRED")

    def test_readonly_github_token_required(self):
        self.blocked(self.source.replace(
            "permissions:\n  contents: read", "permissions:\n  contents: write", 1),
            "READ_ONLY_PERMISSIONS_REQUIRED")

    def test_no_flag_can_turn_on(self):
        for flag in sorted(guard.KILL_SWITCHES):
            with self.subTest(flag=flag):
                old = flag + ": 'false'"
                self.assertIn(old, self.source)
                self.blocked(self.source.replace(old, flag + ": 'true'", 1),
                             "EXTERNAL_EFFECT_ENABLED_" + flag)

    def test_no_outbound_provider_override(self):
        self.blocked(
            self.source.replace("HELVOCA_OUTBOUND_PROVIDER: NONE",
                                "HELVOCA_OUTBOUND_PROVIDER: TWILIO", 1),
            "OUTBOUND_PROVIDER_MUST_BE_NONE")

    def test_full_backend_and_differential_coverage_cannot_be_downgraded(self):
        self.blocked(
            self.source.replace("-Pcoverage test", "-DskipTests package", 1),
            "FULL_BACKEND_JACOCO_REQUIRED")
        self.blocked(
            self.source.replace('DIFF_BRANCH_COVERAGE: "100"',
                                'DIFF_BRANCH_COVERAGE: "95"', 1),
            "APPLICABLE_JAVA_DIFFERENTIAL_100_REQUIRED")
        self.blocked(
            self.source.replace(guard.BASE_SHA, "0" * 40, 1),
            "DIFFERENTIAL_BASE_SHA_DRIFT")

    def test_full_playwright_typecheck_and_coverage_required(self):
        self.blocked(self.source.replace("npm run test:e2e",
                                         "echo skipped", 1),
                     "FULL_FRONTEND_TYPECHECK_PLAYWRIGHT_REQUIRED")
        self.blocked(self.source.replace("npm run frontend:typecheck",
                                         "echo skipped", 1),
                     "FULL_FRONTEND_TYPECHECK_PLAYWRIGHT_REQUIRED")
        self.blocked(self.source.replace(
            "node scripts/ci/check-home-frontend-coverage.cjs",
            "echo skipped", 1),
            "HOME_SOURCE_COVERAGE_REQUIRED")
        self.blocked(self.source.replace(
            "node scripts/ci/check-inventory-frontend-coverage.cjs",
            "echo skipped", 1),
            "INVENTORY_SOURCE_COVERAGE_REQUIRED")

    def test_golden_journey_and_carrier_signature_contract_required(self):
        self.blocked(self.source.replace(
            "bash scripts/ci/pilot-e2e-certification.sh",
            "echo skipped", 1),
            "OFFLINE_GOLDEN_JOURNEY_REQUIRED")
        self.blocked(self.source.replace(
            "TwilioSignatureValidationFilterTest", "RemovedTest", 1),
            "MOCKED_CARRIER_WEBHOOK_REQUIRED")

    def test_tenant_or_routing_negative_test_cannot_disappear(self):
        for name in guard.TENANT_TESTS:
            with self.subTest(name=name):
                self.blocked(self.source.replace(name, "RemovedTest", 1),
                             "PG18_MULTITENANT_ADVERSARIAL_REGRESSION_REQUIRED")

    def test_local_only_pg18_system_stack_cannot_use_remote_database(self):
        self.blocked(self.source.replace(
            "jdbc:postgresql://127.0.0.1:5432/helvoca",
            "jdbc:postgresql://external.invalid/helvoca", 1),
            "ISOLATED_PG18_REACT_SPRING_E2E_REQUIRED")

    def test_static_guard_and_prior_safety_gates_cannot_be_skipped(self):
        self.blocked(self.source.replace(
            "python3 scripts/ci/voice_part9_integrated_gate_contract.py",
            "echo skipped", 1),
            "NEW_FAIL_CLOSED_CONTRACT_REQUIRED")
        self.blocked(self.source.replace(
            "test_voice_part8_model_cost_consent_gate.py",
            "echo skipped", 1),
            "EARLIER_SECURITY_GATES_REQUIRED")

    def test_no_networked_production_mutations_or_secrets(self):
        for value in guard.FORBIDDEN:
            with self.subTest(value=value):
                self.blocked(self.source + "\n# " + value + "\n",
                             "FORBIDDEN_PRODUCTION_OPERATION_OR_SECRET")

    def test_no_pretend_go_on_positive_definition(self):
        for key in ("may_merge", "may_call", "live_call_certified", "commercial_go"):
            self.assertFalse(guard.assess(self.source)[key])


if __name__ == "__main__":
    unittest.main()
