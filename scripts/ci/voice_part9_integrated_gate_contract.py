#!/usr/bin/env python3
"""Part 9/10: fail-closed audit of the eight full offline release gates.

Only checks a workflow text file. A local PASS proves the safe definition,
never real execution, real provider connectivity, a production backup or GO.
"""
from __future__ import annotations

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/voice-release-part9-full-regression-offline.yml"
BASE_SHA = "25f45a898ec531d90d06a7e547e0a16f1f4fae3a"
PART8_BASE = "test/voice-release-part8-gemini-cost-consent-offline-*"
PART9_BRANCH = "test/voice-release-part9-full-regression-offline-*"
JOBS = {
    "meta-webhook-offline-contract",
    "golden-journey-release-contract",
    "fast-gate",
    "test",
    "inventory-frontend-coverage",
    "home-frontend-coverage",
    "system-integration-e2e",
    "part9-cross-tenant-voice-adversarial",
}
KILL_SWITCHES = {
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
}
FORBIDDEN = (
    "workflow_dispatch:",
    "meta-webhook-public-smoke:",
    "https://recepvoz.cl",
    "https://helvoca-api-production.up.railway.app",
    "secrets.",
    "railway ssh",
    "railway up",
    "railway deploy",
    "gh pr merge",
    "git push origin main",
    "flyway migrate",
    "continue-on-error: true",
    "PROD_DEPLOY=GO",
    "COMMERCIAL_GO=true",
    "LIVE_CALL_CERTIFIED=true",
)
TENANT_TESTS = (
    "TwilioPhoneTenantBoundaryPostgresIntegrationTest",
    "PostgresRowLevelSecurityIntegrationTest",
    "TwilioExistingEnrollmentAdminPostgresIntegrationTest",
    "TwilioExistingEnrollmentReconcilerPostgresIntegrationTest",
    "TwilioVerifiedExistingVoiceRoutePostgresIntegrationTest",
    "RealtimeToolTenantScopeIntegrationTest",
    "TwilioMediaStreamStartupRecoveryPhase7Test",
    "GeminiLiveRuntimeReadinessTest",
)


def assess(source: object) -> dict:
    failures = []

    def require(ok: bool, code: str) -> None:
        if not ok:
            failures.append(code)

    if not isinstance(source, str):
        source = ""
        failures.append("INVALID_OR_MISSING_WORKFLOW")
    header, delimiter, body = source.partition("\njobs:\n")
    require(bool(delimiter), "JOBS_MISSING")
    keys = set(re.findall(r"(?m)^  ([a-z][a-z0-9-]*):\s*$", body))
    require(keys == JOBS, "EIGHT_GATES_EXACTLY_REQUIRED")
    require(PART9_BRANCH in header and PART8_BASE in header
            and "    - 'main'" not in header
            and 'branches: ["main"]' not in header,
            "STACKED_BRANCH_TRIGGER_ONLY")
    require("permissions:\n  contents: read" in header,
            "READ_ONLY_PERMISSIONS_REQUIRED")
    for key in KILL_SWITCHES:
        require(bool(re.search(
            rf"(?m)^  {re.escape(key)}: ['\"]false['\"]\s*$", header)),
            "EXTERNAL_EFFECT_ENABLED_" + key)
    require("  HELVOCA_OUTBOUND_PROVIDER: NONE" in header,
            "OUTBOUND_PROVIDER_MUST_BE_NONE")
    require(body.count(BASE_SHA) == 2, "DIFFERENTIAL_BASE_SHA_DRIFT")
    require("mvn --batch-mode --no-transfer-progress -Pcoverage test" in body,
            "FULL_BACKEND_JACOCO_REQUIRED")
    require(all(f'DIFF_{level}_COVERAGE: "100"' in body
                for level in ("LINE", "BRANCH", "METHOD")),
            "APPLICABLE_JAVA_DIFFERENTIAL_100_REQUIRED")
    require("npm run frontend:typecheck" in body
            and "npm run test:e2e" in body,
            "FULL_FRONTEND_TYPECHECK_PLAYWRIGHT_REQUIRED")
    require("npx playwright test e2e/react-inventory*.spec.js" in body
            and "node scripts/ci/check-inventory-frontend-coverage.cjs" in body,
            "INVENTORY_SOURCE_COVERAGE_REQUIRED")
    require("npx playwright test e2e/react-home.spec.js" in body
            and "node scripts/ci/check-home-frontend-coverage.cjs" in body,
            "HOME_SOURCE_COVERAGE_REQUIRED")
    require("bash scripts/ci/pilot-e2e-certification.sh" in body,
            "OFFLINE_GOLDEN_JOURNEY_REQUIRED")
    require("TwilioSignatureValidationFilterTest" in body
            and "TwilioExistingNumberOwnershipVerifierTest" in body,
            "MOCKED_CARRIER_WEBHOOK_REQUIRED")
    require("python3 scripts/ci/voice_part9_integrated_gate_contract.py" in body
            and "test_voice_part9_integrated_gate_contract.py" in body,
            "NEW_FAIL_CLOSED_CONTRACT_REQUIRED")
    require("python3 scripts/ci/voice_part5_integrated_gate_contract.py" in body
            and "test_voice_part8_model_cost_consent_gate.py" in body,
            "EARLIER_SECURITY_GATES_REQUIRED")
    require("image: postgres:18" in body
            and "DB_URL: jdbc:postgresql://127.0.0.1:5432/helvoca" in body
            and "SYSTEM_E2E_BASE_URL: http://127.0.0.1:8080" in body
            and "bash scripts/ci/system-e2e.sh" in body,
            "ISOLATED_PG18_REACT_SPRING_E2E_REQUIRED")
    job_name = "part9-cross-tenant-voice-adversarial"
    parts = body.split("\n  " + job_name + ":\n", 1)
    part9 = parts[1] if len(parts) == 2 else ""
    require("postgres:18-alpine" in part9 and
            all(test in part9 for test in TENANT_TESTS),
            "PG18_MULTITENANT_ADVERSARIAL_REGRESSION_REQUIRED")
    for command in FORBIDDEN:
        require(command.lower() not in source.lower(),
                "FORBIDDEN_PRODUCTION_OPERATION_OR_SECRET")
    return {
        "part": "9/10",
        "definition": "PASS_OFFLINE_DEFINITION" if not failures else "BLOCKED",
        "blockers": sorted(set(failures)),
        "expected_jobs": 8,
        "executed_ci": "NOT_PROVEN_BY_STATIC_AUDITOR",
        "full_backend_tests": "NOT_PROVEN_BY_STATIC_AUDITOR",
        "full_browser_tests": "NOT_PROVEN_BY_STATIC_AUDITOR",
        "source_trusted": False,
        "prod_deploy": "NO-GO",
        "may_merge": False,
        "may_call": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


def main() -> int:
    try:
        doc = WORKFLOW.read_text(encoding="utf-8")
    except (UnicodeError, OSError):
        doc = None
    result = assess(doc)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["definition"] == "PASS_OFFLINE_DEFINITION" else 2


if __name__ == "__main__":
    raise SystemExit(main())
