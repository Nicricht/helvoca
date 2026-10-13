#!/usr/bin/env python3
"""Helvoca Part 5 offline seven-gate workflow contract, always fail-closed.

This script reads the repository workflow only. It never invokes a provider,
GitHub API, Twilio, Railway, PostgreSQL, public domain, shell, or deployment.
A PASS validates static safeguards, NOT test completion or release permission.
"""
from __future__ import annotations

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/voice-release-part5-integrated-offline.yml"
EXPECTED_BASE = "25f45a898ec531d90d06a7e547e0a16f1f4fae3a"
EXPECTED_GATES = {
    "meta-webhook-offline-contract",
    "golden-journey-release-contract",
    "fast-gate",
    "test",
    "inventory-frontend-coverage",
    "home-frontend-coverage",
    "system-integration-e2e",
}
REQUIRED_SAFE_ENV = {
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
DISALLOWED = (
    "https://helvoca-api-production.up.railway.app",
    "https://recepvoz.cl",
    "railway up", "railway deploy", "railway ssh",
    "twilio api:", "curl -X POST", "flyway migrate",
    "gh pr merge", "git push origin main",
    "PROD_DEPLOY=GO", "COMMERCIAL_GO=true",
    "LIVE_CALL_CERTIFIED=true",
)


def assess(source: object) -> dict:
    blockers = []

    def need(predicate, code):
        if not predicate:
            blockers.append(code)

    if not isinstance(source, str):
        return {"part": "5/10", "contract": "BLOCKED",
                "blockers": ["WORKFLOW_UNAVAILABLE"],
                "release": "NO-GO", "may_merge": False,
                "may_deploy": False, "live_call_certified": False,
                "commercial_go": False}
    header, sep, body = source.partition("\njobs:\n")
    need(bool(sep), "JOB_BLOCK_MISSING")
    jobs = set(re.findall(r"^  ([a-z][a-z0-9-]*):\s*$", body, re.M))
    need(jobs == EXPECTED_GATES, "SEVEN_GATES_INCOMPLETE_OR_REPLACED")
    need("test/voice-release-part5-integrated-offline-*" in header,
         "BRANCH_TRIGGER_WRONG")
    need("test/voice-release-part4-twilio-reconciliation-*" in header,
         "SAFE_PARENT_PR_TRIGGER_MISSING")
    need("branches: [\"main\"]" not in header
         and "    - 'main'" not in header, "MAIN_TRIGGER_FORBIDDEN")
    need("permissions:\n  contents: read" in header, "READONLY_CONTENTS_PERMISSION_MISSING")
    for key in sorted(REQUIRED_SAFE_ENV):
        need(bool(re.search(r"(?m)^  " + re.escape(key) + r": ['\"]false['\"]\s*$",
                            header)), "EXTERNAL_FLAG_NOT_DISABLED_" + key)
    need("  HELVOCA_OUTBOUND_PROVIDER: NONE" in header,
         "EXTERNAL_PROVIDER_NOT_DISABLED")
    need("image: postgres:18" in body and "image: postgres:16" not in body,
         "PG18_SYSTEM_INTEGRATION_REQUIRED")
    need(body.count(EXPECTED_BASE) == 2,
         "BASE_SHA_MUST_POINT_TO_PART3_FOR_PART4_COVERAGE")
    need("mvn --batch-mode --no-transfer-progress -Pcoverage test" in body,
         "COMPLETE_JAVA_SUITE_NOT_RUN")
    need("npm run frontend:typecheck" in body
         and "npm run test:e2e" in body, "REACT_OR_PLAYWRIGHT_NOT_RUN")
    need("npm run frontend:build" in body,
         "INSTRUMENTED_REACT_BUILD_NOT_RUN")
    need("bash scripts/ci/system-e2e.sh" in body
         and "http://127.0.0.1:8080" in body,
         "LOCAL_SYSTEM_E2E_REQUIRED")
    need("npx playwright test e2e/react-inventory*.spec.js" in body,
         "INVENTORY_COVERAGE_MISSING")
    need("npx playwright test e2e/react-home.spec.js" in body,
         "HOME_COVERAGE_MISSING")
    need("TwilioSignatureValidationFilterTest" in body
         and "TwilioExistingNumberOwnershipVerifierTest" in body,
         "WEBHOOK_CONTRACT_WITHOUT_PUBLIC_HTTP_MISSING")
    need('DIFF_LINE_COVERAGE: "100"' in body
         and 'DIFF_BRANCH_COVERAGE: "100"' in body
         and 'DIFF_METHOD_COVERAGE: "100"' in body,
         "STRICT_JAVA_DIFF_COVERAGE_MISSING")
    for forbidden in DISALLOWED:
        need(forbidden.lower() not in source.lower(),
             "UNSAFE_WORKFLOW_COMMAND_OR_REMOTE_ENDPOINT")
    # This job must not call an unsafe workflow reference or suppress errors.
    need("continue-on-error: true" not in body
         and "|| true" not in body,
         "GATES_MUST_FAIL_CLOSED")
    need("meta-webhook-public-smoke:" not in source,
         "PUBLIC_WEBHOOK_SMOKE_FORBIDDEN")
    return {
        "part": "5/10",
        "contract": "PASS_OFFLINE_DEFINITION" if not blockers else "BLOCKED",
        "blockers": sorted(set(blockers)),
        "gates_required": 7,
        "gates_executed": "NOT_DETERMINED_BY_THIS_SCRIPT",
        "github_main": "UNCHANGED_NOT_CHECKED_HERE",
        "release": "NO-GO",
        "may_merge": False,
        "may_deploy": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


def main() -> int:
    try:
        result = assess(WORKFLOW.read_text(encoding="utf-8"))
    except (UnicodeError, OSError):
        result = assess(None)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["contract"] == "PASS_OFFLINE_DEFINITION" else 2


if __name__ == "__main__":
    raise SystemExit(main())
