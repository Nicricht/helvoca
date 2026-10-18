#!/usr/bin/env python3
"""Stage C: offline, fail-closed release lineage and production-readiness audit.

This program reads ONLY an already-supplied JSON snapshot. It never accesses
Twilio, Railway, GitHub or PostgreSQL and never authorizes an action. Even a
PASS-shaped self-report requires separate verification and operator review.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys

MAIN_BEFORE_RELEASE = "ca58693e167752bd6eab8ff4e9be770391fe4293"
STAGE_B_SHA = "9c1810457468fc2be8ab8cfe105af8c7448ac324"
BACKUP_SHA256 = "46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18"
PHONE_VOLUME = "b28c1046-168e-4f2e-bf02-18a2a2f1e611"
SHA = re.compile(r"^[a-f0-9]{40}$")

# PR 805 is not part of this chain. A PR's BASE is the previous PR's HEAD
# branch, NOT the name of its associated pull request.
CHAIN = (
    (793, "integration/voice-main-phase2-candidate-20261012", "audit/twilio-railway-offline-phase3-20261012"),
    (794, "audit/twilio-railway-offline-phase3-20261012", "feat/twilio-verified-existing-voice-route-phase4-20261012"),
    (795, "feat/twilio-verified-existing-voice-route-phase4-20261012", "feat/gemini-live-offline-session-config-phase5-20261012"),
    (796, "feat/gemini-live-offline-session-config-phase5-20261012", "test/voice-cross-tenant-postgres-phase6-20261012"),
    (797, "test/voice-cross-tenant-postgres-phase6-20261012", "test/voice-offline-lifecycle-phase7-20261012"),
    (798, "test/voice-offline-lifecycle-phase7-20261012", "test/voice-final-certification-phase8-20261012"),
    (799, "test/voice-final-certification-phase8-20261012", "feat/voice-real-pilot-1of6-preflight-20261012"),
    (800, "feat/voice-real-pilot-1of6-preflight-20261012", "feat/voice-real-pilot-2of6-carrier-binding-20261012"),
    (801, "feat/voice-real-pilot-2of6-carrier-binding-20261012", "feat/voice-real-pilot-3of6-gemini-audio-20261012"),
    (802, "feat/voice-real-pilot-3of6-gemini-audio-20261012", "test/voice-real-pilot-4of6-integrated-release-20261012"),
    (803, "test/voice-real-pilot-4of6-integrated-release-20261012", "chore/voice-real-pilot-5of6-production-gate-20261012"),
    (804, "chore/voice-real-pilot-5of6-production-gate-20261012", "test/voice-real-pilot-6of6-live-evidence-20261012"),
    (806, "test/voice-real-pilot-6of6-live-evidence-20261012", "chore/voice-pilot-post6-railway-recovery-audit-20261013"),
    (807, "chore/voice-pilot-post6-railway-recovery-audit-20261013", "test/voice-pilot-stage-b-pg18-migration-rehearsal-20261013"),
)


def section(doc: object, field: str) -> dict:
    obj = doc.get(field) if isinstance(doc, dict) else None
    return obj if isinstance(obj, dict) else {}


@dataclass(frozen=True)
class Assessment:
    state: str
    blockers: tuple[str, ...]

    def as_dict(self) -> dict:
        return {
            "state": self.state,
            "blockers": list(self.blockers),
            "may_merge": False,
            "may_deploy": False,
            "may_run_live_call": False,
            "commercial_go": False,
            "independent_evidence_review_required": True,
        }


def assess(doc: object) -> Assessment:
    if not isinstance(doc, dict):
        return Assessment("BLOCKED", ("INVALID_SNAPSHOT",))
    errors: list[str] = []

    def require(ok: bool, code: str) -> None:
        if not ok:
            errors.append(code)

    git = section(doc, "git")
    railway = section(doc, "railway")
    ci = section(doc, "ci")
    postgres = section(doc, "postgres")
    controls = section(doc, "controls")

    main_sha = git.get("main_sha")
    require(git.get("main_branch") == "main"
            and git.get("baseline_main_sha") == MAIN_BEFORE_RELEASE
            and main_sha in (MAIN_BEFORE_RELEASE, STAGE_B_SHA),
            "PRODUCTION_MAIN_BASELINE_CHANGED")
    candidate = git.get("candidate_sha")
    require(isinstance(candidate, str) and SHA.fullmatch(candidate) is not None
            and candidate == STAGE_B_SHA,
            "CANDIDATE_NOT_PINNED_TO_STAGE_B")
    require(git.get("candidate_branch") == CHAIN[-1][2],
            "CANDIDATE_BRANCH_MISMATCH")

    prs = git.get("pull_requests")
    seen = {}
    if isinstance(prs, list):
        for item in prs:
            if isinstance(item, dict) and type(item.get("number")) is int:
                seen[item["number"]] = item
    require(len(seen) == len(CHAIN), "PR_CHAIN_COUNT_MISMATCH")
    structure_ok = all(
        seen.get(n, {}).get("base") == base and seen.get(n, {}).get("head") == head
        and isinstance(seen.get(n, {}).get("head_sha"), str)
        and SHA.fullmatch(seen[n]["head_sha"]) is not None
        for n, base, head in CHAIN
    )
    require(structure_ok, "PR_CHAIN_BASE_OR_HEAD_MISMATCH")
    require(seen.get(807, {}).get("head_sha") == STAGE_B_SHA,
            "STAGE_B_COMMIT_DRIFT")
    # Open stacked PRs are not a release. Do NOT merge automatically: main is
    # configured as a Railway deployment source.
    require(all(seen.get(n, {}).get("merged") is True for n, _, _ in CHAIN),
            "STACK_HAS_UNMERGED_PRS")

    require(railway.get("project_id") == "41988ee6-f9f6-48eb-b726-abb973bb5740"
            and railway.get("environment_id") == "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
            and railway.get("api_source_branch") == "main",
            "RAILWAY_TARGET_NOT_PINNED")
    require(railway.get("api_status") == "online"
            and railway.get("postgres_status") == "online"
            and type(railway.get("pending_changes")) is int
            and railway["pending_changes"] == 0,
            "RAILWAY_BASELINE_UNHEALTHY_OR_STAGED")
    require(isinstance(railway.get("deployed_git_sha"), str)
            and SHA.fullmatch(railway["deployed_git_sha"]) is not None,
            "RAILWAY_DEPLOYED_SHA_UNVERIFIED")
    require(railway.get("deployed_git_sha") == main_sha,
            "RAILWAY_DEPLOYMENT_NOT_EQUAL_TO_MAIN")
    require(main_sha == candidate, "RELEASE_CANDIDATE_NOT_ON_MAIN")

    require(ci.get("head_sha") == candidate
            and ci.get("conclusion") == "success"
            and type(ci.get("full_gate_jobs")) is int
            and ci["full_gate_jobs"] == 7
            and type(ci.get("passed_jobs")) is int
            and ci["passed_jobs"] == 7
            and isinstance(ci.get("run_url"), str)
            and ci["run_url"].startswith("https://github.com/Nicricht/helvoca/actions/runs/"),
            "EXACT_SHA_SEVEN_GATE_CI_UNVERIFIED")

    require(postgres.get("volume_id") == PHONE_VOLUME, "DATABASE_VOLUME_CHANGED")
    require(postgres.get("stage_a_archive_sha256") == BACKUP_SHA256
            and postgres.get("stage_a_isolated_restore") is True
            and postgres.get("stage_b_copy_migrations_verified") is True,
            "RECOVERY_AND_MIGRATION_REHEARSAL_NOT_VERIFIED")
    require(postgres.get("fresh_backup_for_release_verified") is True,
            "FRESH_PRE_RELEASE_BACKUP_UNVERIFIED")
    require(postgres.get("production_flyway_read_only_checked") is True,
            "LIVE_FLYWAY_HISTORY_UNVERIFIED")
    require(postgres.get("schema_backward_compatibility_checked") is True,
            "ROLLBACK_SCHEMA_COMPATIBILITY_UNVERIFIED")
    require(postgres.get("independent_encrypted_backup_verified") is True,
            "OFFSITE_ENCRYPTED_RECOVERY_UNVERIFIED")

    require(controls.get("carrier_twilio_ownership_checked") is True
            and controls.get("pilot_tenant_sid_binding_checked") is True,
            "LIVE_CARRIER_OWNERSHIP_UNVERIFIED")
    require(controls.get("global_call_effects_disabled") is True
            and controls.get("other_tenants_protected") is True,
            "VOICE_SAFETY_FENCE_UNVERIFIED")
    require(controls.get("maintenance_window_approved") is True
            and controls.get("rollback_operator_assigned") is True
            and controls.get("incident_stop_plan_verified") is True,
            "HUMAN_ROLLOUT_PLAN_UNVERIFIED")

    return Assessment("BLOCKED" if errors else "READY_FOR_INDEPENDENT_RELEASE_REVIEW",
                      tuple(errors))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--expect-blocked", action="store_true")
    args = parser.parse_args(argv)
    try:
        data = json.loads(args.evidence.read_text(encoding="utf-8"))
        output = assess(data)
    except (OSError, ValueError):
        output = Assessment("BLOCKED", ("SNAPSHOT_UNREADABLE",))
    print(json.dumps(output.as_dict(), indent=2, sort_keys=True))
    if args.expect_blocked:
        return 0 if output.state == "BLOCKED" else 1
    return 2 if output.state == "BLOCKED" else 3


if __name__ == "__main__":
    sys.exit(main())
