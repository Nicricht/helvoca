#!/usr/bin/env python3
"""Read-only, fail-closed production release checklist. No API calls or mutations.

Evidence is *operator-supplied*, NOT cryptographic attestation. A PASS is never
sufficient authorization to deploy or to start an actual Twilio call.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

SHA_RE = re.compile(r"^[a-f0-9]{40}$")
BASELINE_MAIN_SHA = "ca58693e167752bd6eab8ff4e9be770391fe4293"
EXPECTED_MIGRATIONS = (
    "V100__twilio_managed_voice_binding_provenance.sql",
    "V101__twilio_verified_existing_binding_provenance.sql",
    "V102__twilio_existing_enrollment_claim.sql",
    "V103__twilio_existing_enrollment_admin_approval.sql",
)


@dataclass(frozen=True)
class GateResult:
    state: str
    blockers: tuple[str, ...]
    live_call_certified: bool = False
    commercial_go: bool = False

    def as_dict(self) -> dict:
        return {
            "state": self.state,
            "blockers": list(self.blockers),
            "live_call_certified": False,
            "commercial_go": False,
            "is_deployment_authorization": False,
        }


def check(evidence: dict) -> GateResult:
    """Require positive, separately corroborated release evidence.

    Caller must verify original artifacts outside this script. Fabricating or
    copying a PASS-shaped JSON does not constitute operator approval.
    """
    if not isinstance(evidence, dict):
        return GateResult("BLOCKED", ("INVALID_EVIDENCE",))
    errors: list[str] = []

    def require(condition: bool, code: str) -> None:
        if not condition:
            errors.append(code)

    def section(value: object) -> dict:
        return value if isinstance(value, dict) else {}

    baseline = section(evidence.get("baseline"))
    release = section(evidence.get("release"))
    postgres = section(evidence.get("postgres"))
    safeguards = section(evidence.get("safeguards"))

    require(baseline.get("service_name") == "helvoca-api", "SERVICE_NOT_PINNED")
    require(baseline.get("production_commit") == BASELINE_MAIN_SHA, "PRODUCTION_BASELINE_CHANGED")
    require(baseline.get("production_branch") == "main", "PRODUCTION_BRANCH_UNEXPECTED")
    require(baseline.get("staged_changes") == 0, "STAGED_PRODUCTION_CHANGES")

    commit = release.get("candidate_commit")
    require(isinstance(commit, str) and SHA_RE.fullmatch(commit) is not None
            and commit != BASELINE_MAIN_SHA, "CANDIDATE_COMMIT_NOT_PINNED")
    require(release.get("full_ci_sha") == commit
            and release.get("full_ci_conclusion") == "success"
            and release.get("full_ci_job_count") == 7
            and release.get("full_ci_successful_jobs") == 7,
            "CANDIDATE_FULL_CI_NOT_CERTIFIED")

    require(release.get("approved_change_scope") == "voice-pilot-one-tenant",
            "RELEASE_SCOPE_NOT_PINNED")
    require(release.get("maintenance_window_confirmed") is True,
            "MAINTENANCE_WINDOW_NOT_CONFIRMED")
    require(release.get("rollback_baseline_commit") == BASELINE_MAIN_SHA,
            "ROLLBACK_CODE_BASELINE_NOT_PINNED")

    require(postgres.get("service_name") == "Postgres"
            and postgres.get("volume_id") == "b28c1046-168e-4f2e-bf02-18a2a2f1e611",
            "POSTGRES_TARGET_CHANGED")
    require(postgres.get("migrations_to_apply") == list(EXPECTED_MIGRATIONS),
            "MIGRATION_SET_NOT_REVIEWED")
    require(postgres.get("migration_compatibility_reviewed") is True,
            "MIGRATION_COMPATIBILITY_NOT_REVIEWED")
    require(postgres.get("flyway_history_read_only_confirmed") is True,
            "LIVE_FLYWAY_HISTORY_NOT_READ")

    backup = section(postgres.get("backup"))
    require(bool(backup.get("backup_id"))
            and backup.get("snapshot_freshness_verified") is True,
            "FRESH_BACKUP_NOT_VERIFIED")
    require(bool(backup.get("restore_evidence_id"))
            and backup.get("isolated_restore_completed") is True
            and backup.get("source_volume_untouched") is True,
            "ISOLATED_RESTORE_NOT_CERTIFIED")

    require(safeguards.get("global_pilot_effects_disabled") is True,
            "PILOT_GLOBAL_KILL_SWITCH_NOT_VERIFIED")
    require(safeguards.get("other_tenants_unchanged") is True,
            "OTHER_TENANTS_NOT_PROTECTED")
    require(safeguards.get("health_rollback_operator_assigned") is True,
            "ROLLBACK_OPERATOR_NOT_ASSIGNED")

    return GateResult("READY_FOR_HUMAN_DEPLOYMENT_REVIEW" if not errors else "BLOCKED",
                      tuple(errors))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--expect-blocked", action="store_true",
                        help="CI regression: incomplete baseline must stay blocked")
    args = parser.parse_args(argv)

    try:
        data = json.loads(args.evidence.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        result = GateResult("BLOCKED", ("EVIDENCE_UNREADABLE",))
    else:
        result = check(data)
    print(json.dumps(result.as_dict(), indent=2, sort_keys=True))
    if args.expect_blocked:
        return 0 if result.state == "BLOCKED" else 1
    return 0 if result.state == "READY_FOR_HUMAN_DEPLOYMENT_REVIEW" else 2


if __name__ == "__main__":
    sys.exit(main())
