"""Part 5 fail-closed seven-gate GitHub workflow static-contract regression tests."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "part5_guard", ROOT / "scripts/ci/voice_part5_integrated_gate_contract.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class Part5OfflineSafetyContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = guard.WORKFLOW.read_text(encoding="utf-8")

    def blocked(self, changed, code):
        result = guard.assess(changed)
        self.assertEqual("BLOCKED", result["contract"])
        self.assertIn(code, result["blockers"])
        self.assertEqual("NO-GO", result["release"])
        self.assertFalse(result["commercial_go"])

    def test_production_off_and_seven_offline_gates_present(self):
        result = guard.assess(self.original)
        self.assertEqual("PASS_OFFLINE_DEFINITION", result["contract"],
                         result["blockers"])
        self.assertEqual(7, result["gates_required"])
        self.assertFalse(result["may_merge"])
        self.assertFalse(result["may_deploy"])
        self.assertFalse(result["live_call_certified"])

    def test_missing_or_extra_seventh_job_fails(self):
        self.blocked(self.original.replace("  system-integration-e2e:\n", ""),
                     "SEVEN_GATES_INCOMPLETE_OR_REPLACED")
        self.blocked(self.original + "\n  unsafe-eighth-job:\n    runs-on: ubuntu-latest\n",
                     "SEVEN_GATES_INCOMPLETE_OR_REPLACED")

    def test_public_production_webhook_cannot_be_called(self):
        self.blocked(self.original + "\nhttps://recepvoz.cl\n",
                     "UNSAFE_WORKFLOW_COMMAND_OR_REMOTE_ENDPOINT")
        self.blocked(self.original + "\nhttps://helvoca-api-production.up.railway.app\n",
                     "UNSAFE_WORKFLOW_COMMAND_OR_REMOTE_ENDPOINT")

    def test_main_branch_would_be_invalid_trigger(self):
        self.blocked(self.original.replace(
            "      - 'test/voice-release-part5-integrated-offline-*'",
            "      - 'main'"), "MAIN_TRIGGER_FORBIDDEN")

    def test_readonly_contents_permission_mandatory(self):
        self.blocked(self.original.replace(
            "permissions:\n  contents: read", "permissions:\n  contents: write"),
            "READONLY_CONTENTS_PERMISSION_MISSING")

    def test_all_external_effect_kill_switches_fail_closed(self):
        for flag in guard.REQUIRED_SAFE_ENV:
            with self.subTest(flag=flag):
                old=f"  {flag}: 'false'"
                self.assertIn(old, self.original)
                self.blocked(self.original.replace(old, f"  {flag}: 'true'"),
                             "EXTERNAL_FLAG_NOT_DISABLED_" + flag)

    def test_pg18_and_loopback_full_system_e2e_required(self):
        self.blocked(self.original.replace("image: postgres:18", "image: postgres:16"),
                     "PG18_SYSTEM_INTEGRATION_REQUIRED")
        self.blocked(self.original.replace("http://127.0.0.1:8080",
                                           "https://external.example"),
                     "LOCAL_SYSTEM_E2E_REQUIRED")

    def test_no_deployment_commands(self):
        for command in ("railway up", "railway ssh", "git push origin main",
                        "gh pr merge", "curl -X POST", "flyway migrate"):
            with self.subTest(command=command):
                self.blocked(self.original + "\n" + command,
                             "UNSAFE_WORKFLOW_COMMAND_OR_REMOTE_ENDPOINT")

    def test_diff_coverage_minimums_cannot_decrease(self):
        self.blocked(self.original.replace(
            'DIFF_BRANCH_COVERAGE: "100"', 'DIFF_BRANCH_COVERAGE: "90"'),
            "STRICT_JAVA_DIFF_COVERAGE_MISSING")

    def test_no_unverified_statuses_or_unproved_writes(self):
        for claim in ("PROD_DEPLOY=GO", "COMMERCIAL_GO=true", "LIVE_CALL_CERTIFIED=true"):
            self.blocked(self.original + "\n" + claim,
                         "UNSAFE_WORKFLOW_COMMAND_OR_REMOTE_ENDPOINT")

    def test_explicit_fail_open_is_rejected(self):
        self.blocked(self.original + "\ncontinue-on-error: true\n",
                     "GATES_MUST_FAIL_CLOSED")

    def test_missing_react_typecheck_or_playwright_rejected(self):
        self.blocked(self.original.replace("npm run frontend:typecheck",
                                           "echo skipped"),
                     "REACT_OR_PLAYWRIGHT_NOT_RUN")
        self.blocked(self.original.replace("npm run test:e2e", "echo skipped"),
                     "REACT_OR_PLAYWRIGHT_NOT_RUN")

    def test_bad_or_missing_source_rejected(self):
        for bad in (None, {}, "", "on: push", 123):
            with self.subTest(bad=bad):
                result=guard.assess(bad)
                self.assertEqual("NO-GO",result["release"])
                self.assertEqual("BLOCKED",result["contract"])

    def test_no_unsafe_workflow_requires_external_secrets(self):
        self.assertNotIn("secrets.", self.original)
        self.assertNotIn("api.twilio.com", self.original)
        self.assertNotIn("meta-webhook-public-smoke:", self.original)


if __name__ == "__main__":
    unittest.main()
