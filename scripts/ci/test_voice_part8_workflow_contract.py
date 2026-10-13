"""Part 8 offline CI workflow: fail closed on unsafe mutations."""
from __future__ import annotations

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/voice-release-part8-gemini-cost-consent-offline.yml"
EXPECTED = {
    "gemini-financial-consent-stop-contract",
    "gemini-twilio-audio-redaction-unit",
    "pg18-carrier-tenant-rls-regression",
    "react-spring-pg18-local-integration",
}
FLAGS = (
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
    "secrets.", "continue-on-error: true", "railway up",
    "railway deploy", "railway ssh", "gh pr merge",
    "flyway migrate", "git push origin main",
    "up.railway.app", "recepvoz.cl", "upload-artifact",
    "PROD_DEPLOY=GO",
)


def audit(source: str) -> list[str]:
    issues = []
    def require(ok, code):
        if not ok:
            issues.append(code)
    header, delim, body = source.partition("\njobs:\n")
    require(bool(delim), "MISSING_JOBS")
    jobs = set(re.findall(r"(?m)^  ([a-z][a-z0-9-]*):\s*$", body))
    require(jobs == EXPECTED, "EXPECTED_FOUR_JOBS")
    require("permissions:\n  contents: read" in header, "READ_ONLY_PERMISSIONS")
    require("test/voice-release-part8-gemini-cost-consent-offline-*" in header
            and "test/voice-release-part7-predeployment-no-go-*" in header
            and "    - 'main'" not in header, "STACKED_ONLY")
    for flag in FLAGS:
        require(bool(re.search(r"(?m)^  " + flag + r": 'false'\s*$", header)),
                "FLAG_NOT_OFF_" + flag)
    require("  HELVOCA_OUTBOUND_PROVIDER: NONE" in header, "PROVIDER_NOT_OFF")
    for job in EXPECTED - {"gemini-financial-consent-stop-contract"}:
        parts = body.split("\n  " + job + ":\n", 1)
        block = parts[1] if len(parts) == 2 else ""
        block = re.split(r"(?m)^  [a-z][a-z0-9-]*:\s*$", block, maxsplit=1)[0]
        require("    needs: [gemini-financial-consent-stop-contract]" in block,
                "UNGUARDED_JOB")
    require("test_voice_part8_model_cost_consent_gate.py" in body
            and "test_voice_part8_workflow_contract.py" in body
            and "--simulate-blocked" in body and "--simulate-review" in body,
            "SIMULATED_STOP_REQUIRED")
    require("test_voice_part7_predeployment_gate.py" in body
            and "voice_part2_migration_contract.py" in body,
            "PRIOR_FAIL_CLOSED_BASELINE_MISSING")
    require("GeminiLiveModelPolicyTest" in body
            and "GeminiLiveRuntimeReadinessTest" in body
            and "TwilioVoiceCallbackRedactionPhase7Test" in body
            and "TwilioMediaStreamStartupRecoveryPhase7Test" in body
            and "CallCommercialPropertiesTest" in body,
            "VOICE_AUDIO_REDACTION_NOT_TESTED")
    require("PostgresRowLevelSecurityIntegrationTest" in body
            and "TwilioExistingEnrollmentAdminPostgresIntegrationTest" in body
            and "postgres:18-alpine" in body, "PG18_RLS_NOT_TESTED")
    require("services:\n      postgres:\n        image: postgres:18" in body
            and "jdbc:postgresql://127.0.0.1:5432/helvoca" in body
            and "bash scripts/ci/system-e2e.sh" in body,
            "LOCAL_PG18_BROWSER_TEST_REQUIRED")
    for forbidden in FORBIDDEN:
        require(forbidden.lower() not in source.lower(),
                "FORBIDDEN_EXTERNAL_OPERATION")
    return sorted(set(issues))


class Part8WorkflowGuards(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_valid_source_passes(self):
        self.assertEqual([], audit(self.source))

    def test_required_job_cannot_be_removed(self):
        self.assertIn("EXPECTED_FOUR_JOBS", audit(self.source.replace(
            "  pg18-carrier-tenant-rls-regression:", "  other-job:")))

    def test_no_write_permissions(self):
        self.assertIn("READ_ONLY_PERMISSIONS", audit(
            self.source.replace("contents: read", "contents: write", 1)))

    def test_no_main_trigger(self):
        self.assertIn("STACKED_ONLY", audit(
            self.source.replace("jobs:\n", "    - 'main'\n\njobs:\n", 1)))

    def test_no_paid_live_flags(self):
        for flag in FLAGS:
            with self.subTest(flag=flag):
                self.assertIn("FLAG_NOT_OFF_" + flag, audit(
                    self.source.replace(flag + ": 'false'",
                                        flag + ": 'true'", 1)))

    def test_no_unguarded_ci(self):
        self.assertIn("UNGUARDED_JOB", audit(
            self.source.replace(
                "    needs: [gemini-financial-consent-stop-contract]",
                "    needs: []", 1)))

    def test_no_production_commands_or_secrets(self):
        for forbidden in FORBIDDEN:
            with self.subTest(item=forbidden):
                self.assertIn("FORBIDDEN_EXTERNAL_OPERATION",
                              audit(self.source + "\n# " + forbidden))

    def test_model_and_consent_negative_scenario_required(self):
        self.assertIn("SIMULATED_STOP_REQUIRED", audit(
            self.source.replace("--simulate-blocked", "--simulate-skipped", 1)))

    def test_java_readiness_redaction_required(self):
        self.assertIn("VOICE_AUDIO_REDACTION_NOT_TESTED", audit(
            self.source.replace("GeminiLiveRuntimeReadinessTest",
                                "RemovedReadinessTest")))

    def test_rls_and_local_browser_required(self):
        self.assertIn("PG18_RLS_NOT_TESTED", audit(
            self.source.replace("PostgresRowLevelSecurityIntegrationTest",
                                "RemovedRLS")))
        self.assertIn("LOCAL_PG18_BROWSER_TEST_REQUIRED", audit(
            self.source.replace("jdbc:postgresql://127.0.0.1:5432/helvoca",
                                "jdbc:postgresql://db.example.invalid:5432/helvoca")))

    def test_source_model_identity_matches_java_properties_and_yaml(self):
        policy = (ROOT / "src/main/java/cl/helvoca/ai/gemini/GeminiLiveModelPolicy.java").read_text()
        props = (ROOT / "src/main/java/cl/helvoca/ai/gemini/GeminiLiveProperties.java").read_text()
        config = (ROOT / "src/main/resources/application.yml").read_text()
        gate = (ROOT / "scripts/ci/voice_part8_model_cost_consent_gate.py").read_text()
        self.assertIn('DEFAULT_MODEL = "gemini-3.8-live"', policy)
        self.assertIn('private String model = "gemini-3.8-live"', props)
        self.assertIn("GEMINI_LIVE_MODEL:gemini-3.8-live}", config)
        self.assertIn("GEMINI_LIVE_ENABLED:false}", config)
        probe = (ROOT / "src/main/java/cl/helvoca/ai/gemini/GeminiLiveStartupProbe.java").read_text()
        self.assertIn("GEMINI_LIVE_PROBE_ON_STARTUP:false}", probe)
        self.assertIn('EXPECTED_MODEL = "gemini-3.8-live"', gate)
        self.assertIn("A model name being listed here does not prove", policy)

    def test_simulator_has_no_network_or_process_clients(self):
        gate = (ROOT / "scripts/ci/voice_part8_model_cost_consent_gate.py").read_text()
        for bad in ("import requests", "import subprocess", "urllib.request",
                    "socket.socket(", "TWILIO_AUTH_TOKEN", "GEMINI_API_KEY",
                    "PGPASSWORD", "railway ssh", "os.system("):
            self.assertNotIn(bad, gate)


if __name__ == "__main__":
    unittest.main()
