#!/usr/bin/env python3
"""Part 6/10: static, fail-closed preflight for isolated recovery CI.

Never connects to Railway, PostgreSQL, Twilio, Gemini or any network.
Successful validation certifies only the offline definition, not backups.
"""
from __future__ import annotations

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/voice-release-part6-recovery-offline.yml"
DRILL = ROOT / "scripts/ci/voice_part6_synthetic_pg18_recovery.sh"
EXPECTED_JOBS = {
    "offline-safety-contract",
    "synthetic-pg18-restore-and-encryption",
    "pg18-flyway-rls-regression",
}
OFF_FLAGS = (
    "HELVOCA_OUTBOUND_DELIVERY_ENABLED",
    "HELVOCA_TWILIO_EXISTING_VOICE_ROUTE_ENABLED",
    "HELVOCA_TWILIO_SUBACCOUNT_VOICE_CONFIGURE_ON_STARTUP",
    "TWILIO_PROVISIONING_ENABLED",
    "TWILIO_CERTIFICATION_CALL_ON_STARTUP",
    "TWILIO_CERTIFICATION_COMMAND_RUNNER_ENABLED",
    "GEMINI_LIVE_ENABLED",
    "OPENAI_LIVE_ENABLED",
    "APP_JOBS_ENABLED",
)
FORBIDDEN = (
    "railway ssh", "railway up", "railway deploy", "flyway migrate",
    "pg_restore -h", "pg_dump -h", "--publish", "-p 5432",
    "curl ", "wget ", "gh pr merge", "git push origin main",
    "secrets.", "continue-on-error: true", "upload-artifact",
    "recepvoz.cl", "up.railway.app",
)


def assess(workflow: str, drill: str) -> dict:
    blockers = []

    def require(ok: bool, reason: str) -> None:
        if not ok:
            blockers.append(reason)

    require(bool(workflow and drill), "SOURCE_MISSING")
    header, sep, jobs_body = workflow.partition("\njobs:\n")
    require(bool(sep), "JOBS_MISSING")
    jobs = set(re.findall(r"(?m)^  ([a-z][a-z0-9-]*):\s*$", jobs_body))
    require(jobs == EXPECTED_JOBS, "RECOVERY_JOBS_MISMATCH")
    require("test/voice-release-part6-recovery-offline-*" in header
            and "test/voice-release-part5-integrated-offline-*" in header,
            "STACKED_ONLY_TRIGGERS_REQUIRED")
    require("    - 'main'" not in header and 'branches: ["main"]' not in header,
            "MAIN_TRIGGER_FORBIDDEN")
    require("permissions:\n  contents: read" in header,
            "READ_ONLY_GITHUB_PERMISSIONS_REQUIRED")
    for flag in OFF_FLAGS:
        require(bool(re.search(
            r"(?m)^  " + re.escape(flag) + r": ['\"]false['\"]\s*$", header)),
            "EXTERNAL_EFFECT_FLAG_NOT_OFF_" + flag)
    require("  HELVOCA_OUTBOUND_PROVIDER: NONE" in header,
            "PROVIDER_MUST_BE_NONE")
    require("bash scripts/ci/voice_part6_synthetic_pg18_recovery.sh" in jobs_body,
            "SYNTHETIC_RESTORE_DRILL_MISSING")
    require("python3 scripts/ci/voice_part2_migration_contract.py" in jobs_body,
            "FROZEN_MIGRATION_CHECK_MISSING")
    require("test_voice_part6_recovery_contract.py" in jobs_body,
            "ADVERSARIAL_CONTRACT_TESTS_MISSING")
    require("TwilioV99ToV103FlywayUpgradePostgresIntegrationTest" in jobs_body
            and "PostgresRowLevelSecurityIntegrationTest" in jobs_body
            and "TwilioExistingEnrollmentAdminPostgresIntegrationTest" in jobs_body,
            "REAL_FLYWAY_RLS_TESTS_MISSING")
    require("postgres:18-alpine" in workflow,
            "PG18_TESTCONTAINERS_IMAGE_REQUIRED")
    require("set -euo pipefail" in drill and "trap cleanup EXIT" in drill,
            "STRICT_CLEANUP_REQUIRED")
    require(drill.count("docker run --detach") == 2
            and drill.count("--network none") >= 2,
            "TWO_NETWORK_DISABLED_CONTAINERS_REQUIRED")
    require("docker inspect" in drill and "NetworkMode" in drill,
            "DOCKER_ISOLATION_ASSERTION_REQUIRED")
    require("pg_dump -Fc" in drill and "pg_restore" in drill
            and "--exit-on-error" in drill,
            "RESTORE_CHECK_MISSING")
    require("openssl enc -aes-256-cbc -pbkdf2" in drill
            and "openssl rand -hex 32" in drill
            and "sha256sum" in drill and "cmp" in drill,
            "SYNTHETIC_ENCRYPTION_INTEGRITY_MISSING")
    require("PART6_PRODUCTION_BACKUP=UNVERIFIED" in drill,
            "PRODUCTION_BACKUP_MUST_NOT_BE_CERTIFIED")
    require("PART6_SYNTHETIC_PG18_RESTORE=PASS" in drill,
            "SYNTHETIC_RESULT_MISSING")
    combined = workflow + "\n" + drill
    for forbidden in FORBIDDEN:
        require(forbidden.lower() not in combined.lower(),
                "FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")
    return {
        "part": "6/10",
        "definition": "PASS_OFFLINE_DEFINITION" if not blockers else "BLOCKED",
        "blockers": sorted(set(blockers)),
        "expected_jobs": 3,
        "synthetic_restore_run": "NOT_VERIFIED_BY_STATIC_AUDITOR",
        "fresh_production_backup": "UNVERIFIED",
        "encrypted_offsite_backup": "UNVERIFIED",
        "restored_production_copy": "PREVIOUS_OPERATOR_ATTESTATION_ONLY",
        "rollback_approved": False,
        "prod_deploy": "NO-GO",
        "may_merge": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


def main() -> int:
    try:
        result = assess(
            WORKFLOW.read_text(encoding="utf-8"),
            DRILL.read_text(encoding="utf-8"),
        )
    except (OSError, UnicodeError):
        result = assess("", "")
    print(json.dumps(result, sort_keys=True))
    return 0 if result["definition"] == "PASS_OFFLINE_DEFINITION" else 2


if __name__ == "__main__":
    raise SystemExit(main())
