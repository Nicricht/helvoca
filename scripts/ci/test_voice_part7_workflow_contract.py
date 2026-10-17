"""Part 7 workflow definition: adversarial offline safety checks."""
from __future__ import annotations

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/voice-release-part7-predeployment-no-go.yml"
EXPECTED = {
    "predeployment-stop-decision",
    "restore-regression",
    "flyway-tenant-rollback-regression",
    "react-spring-pg18-system-e2e",
}
FALSE_FLAGS = (
    "HELVOCA_OUTBOUND_DELIVERY_ENABLED",
    "HELVOCA_TWILIO_EXISTING_VOICE_ROUTE_ENABLED",
    "HELVOCA_TWILIO_SUBACCOUNT_VOICE_CONFIGURE_ON_STARTUP",
    "HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED",
    "TWILIO_PROVISIONING_ENABLED",
    "TWILIO_CERTIFICATION_CALL_ON_STARTUP",
    "TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED",
    "GEMINI_LIVE_ENABLED",
    "GEMINI_LIVE_PROBE_ON_STARTUP",
    "OPENAI_LIVE_ENABLED",
    "APP_JOBS_ENABLED",
)
FORBIDDEN = (
    "secrets.", "railway ssh", "railway up", "railway deploy",
    "curl -x post", "twilio api:", "flyway migrate",
    "continue-on-error: true", "git push origin main",
    "gh pr merge", "up.railway.app", "recepvoz.cl",
    "upload-artifact", "PROD_DEPLOY=GO",
)


def audit(text: str) -> list[str]:
    findings = []
    def need(ok, code):
        if not ok:
            findings.append(code)
    header, delim, body = text.partition("\njobs:\n")
    need(bool(delim), "MISSING_JOBS")
    jobs = set(re.findall(r"(?m)^  ([a-z][a-z0-9-]*):\s*$", body))
    need(jobs == EXPECTED, "FOUR_EXPECTED_JOBS_MISSING")
    need("permissions:\n  contents: read" in header, "MISSING_READONLY_PERMISSION")
    need("test/voice-release-part7-predeployment-no-go-*" in header
         and "test/voice-release-part6-recovery-offline-*" in header
         and "    - 'main'" not in header,
         "STACKED_PR_ONLY_REQUIRED")
    for flag in FALSE_FLAGS:
        need(bool(re.search(
            rf"(?m)^  {flag}: 'false'\s*$", header
        )), "EXTERNAL_FLAG_NOT_OFF_" + flag)
    need("  HELVOCA_OUTBOUND_PROVIDER: NONE" in header,
         "OUTBOUND_PROVIDER_MUST_BE_NONE")
    for job in EXPECTED - {"predeployment-stop-decision"}:
        part = body.split("\n  " + job + ":\n", 1)
        need(len(part) == 2 and
             "    needs: [predeployment-stop-decision]" in re.split(r"(?m)^  [a-z][a-z0-9-]*:\s*$", part[1], maxsplit=1)[0],
             "UNGUARDED_DEPENDENT_JOB")
    need("python3 scripts/ci/voice_part7_predeployment_gate.py --demo-blocked" in body
         and "python3 scripts/ci/voice_part7_predeployment_gate.py --demo-optimistic" in body
         and "test_voice_part7_predeployment_gate.py" in body,
         "FAIL_CLOSED_SCENARIOS_MISSING")
    need("python3 scripts/ci/voice_part2_migration_contract.py" in body,
         "FROZEN_FLYWAY_CONTRACT_MISSING")
    need("bash scripts/ci/voice_part6_synthetic_pg18_recovery.sh" in body,
         "NO_NETWORK_RESTORE_MISSING")
    need("TwilioV99ToV103FlywayUpgradePostgresIntegrationTest" in body
         and "PostgresRowLevelSecurityIntegrationTest" in body
         and "TwilioExistingEnrollmentAdminPostgresIntegrationTest" in body,
         "PG18_FLYWAY_RLS_NOT_TESTED")
    need("services:\n      postgres:\n        image: postgres:18" in body
         and "jdbc:postgresql://127.0.0.1:5432/helvoca" in body
         and "bash scripts/ci/system-e2e.sh" in body,
         "LOCAL_SYSTEM_E2E_REQUIRED")
    for item in FORBIDDEN:
        need(item.lower() not in text.lower(), "FORBIDDEN_COMMAND_OR_EXTERNAL_ACCESS")
    return sorted(set(findings))


class Part7WorkflowGuards(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_pristine_workflow_is_safe(self):
        self.assertEqual([], audit(self.source))

    def test_modified_jobs_rejected(self):
        self.assertIn("FOUR_EXPECTED_JOBS_MISSING", audit(
            self.source.replace("  restore-regression:", "  renamed-restore:")))

    def test_main_trigger_rejected(self):
        self.assertIn("STACKED_PR_ONLY_REQUIRED", audit(
            self.source.replace("jobs:\n", "    - 'main'\n\njobs:\n", 1)))

    def test_write_scope_rejected(self):
        self.assertIn("MISSING_READONLY_PERMISSION", audit(
            self.source.replace("contents: read", "contents: write", 1)))

    def test_live_provider_flag_rejected(self):
        for flag in FALSE_FLAGS:
            with self.subTest(flag=flag):
                self.assertIn("EXTERNAL_FLAG_NOT_OFF_" + flag, audit(
                    self.source.replace(flag + ": 'false'", flag + ": 'true'", 1)))

    def test_external_commands_and_secrets_rejected(self):
        for item in FORBIDDEN:
            with self.subTest(item=item):
                self.assertIn("FORBIDDEN_COMMAND_OR_EXTERNAL_ACCESS",
                              audit(self.source + "\n# " + item + "\n"))

    def test_unprotected_dependent_gate_rejected(self):
        self.assertIn("UNGUARDED_DEPENDENT_JOB", audit(
            self.source.replace("    needs: [predeployment-stop-decision]",
                                "    needs: []", 1)))

    def test_no_remote_system_e2e(self):
        self.assertIn("LOCAL_SYSTEM_E2E_REQUIRED", audit(
            self.source.replace("jdbc:postgresql://127.0.0.1:5432/helvoca",
                                "jdbc:postgresql://db.example.invalid:5432/helvoca")))

    def test_no_skipping_flyway_or_security(self):
        self.assertIn("PG18_FLYWAY_RLS_NOT_TESTED", audit(
            self.source.replace("PostgresRowLevelSecurityIntegrationTest", "RemovedTest")))

    def test_no_skipping_no_network_restore(self):
        self.assertIn("NO_NETWORK_RESTORE_MISSING", audit(
            self.source.replace(
                "bash scripts/ci/voice_part6_synthetic_pg18_recovery.sh",
                "echo skip")))

    def test_no_skipping_optimistic_or_negative_decision(self):
        self.assertIn("FAIL_CLOSED_SCENARIOS_MISSING", audit(
            self.source.replace("--demo-blocked", "--demo-skipped", 1)))


if __name__ == "__main__":
    unittest.main()
