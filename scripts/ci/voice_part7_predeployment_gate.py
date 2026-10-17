#!/usr/bin/env python3
"""Helvoca Part 7: deterministic, offline STOP/NO-GO release rehearsal.

Only synthetic untrusted snapshots are accepted. This script NEVER accesses
GitHub, Railway, PostgreSQL, Twilio, Gemini, secrets, or the network.
Passing synthetic gates does not authorize a merge, deploy, SQL, or call.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re

MAIN_SHA = "ca58693e167752bd6eab8ff4e9be770391fe4293"
PART5_SHA = "5a78ec252a6133574dedc4b1b55df2c5d650b219"
PART6_SHA = "8939fd4844eee83bcf74c71c7e0685de24f05f19"
EXPECTED_PARENT_BRANCH = "test/voice-release-part6-recovery-offline-20261013"
EXPECTED_BRANCH = "test/voice-release-part7-predeployment-no-go-20261013"
SHA = re.compile(r"^[0-9a-f]{40}$")
MAX_EVIDENCE_AGE = timedelta(hours=2)
MAX_FUTURE_SKEW = timedelta(minutes=5)


def obj(value: object) -> dict:
    return value if isinstance(value, dict) else {}


def exact_true(value: object) -> bool:
    return type(value) is bool and value is True


def exact_false(value: object) -> bool:
    return type(value) is bool and value is False


def time_utc(value: object) -> datetime | None:
    if not isinstance(value, str) or not value.endswith("Z"):
        return None
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        return None
    return parsed if parsed.utcoffset() == timedelta(0) else None


def assess(snapshot: object, now: datetime | None = None) -> dict:
    """Validate the structure of an UNTRUSTED simulated handover.

    The only success means offline fixture rules hold, not authentic evidence.
    """
    now = now or datetime.now(timezone.utc)
    if now.utcoffset() != timedelta(0):
        raise ValueError("now must be UTC")
    blockers: list[str] = []

    def need(condition: bool, code: str) -> None:
        if not condition:
            blockers.append(code)

    if not isinstance(snapshot, dict):
        snapshot = {}
        blockers.append("INVALID_SNAPSHOT")

    identity = obj(snapshot.get("identity"))
    ci = obj(snapshot.get("ci"))
    db = obj(snapshot.get("database"))
    backup = obj(snapshot.get("recovery"))
    voice = obj(snapshot.get("voice"))
    safety = obj(snapshot.get("safety"))
    human = obj(snapshot.get("human"))

    # A user-supplied JSON file cannot authenticate a GitHub run, operator,
    # real backup, DB transaction, Twilio account or Railway state.
    need(snapshot.get("source") == "synthetic",
         "AUTHENTIC_EXTERNAL_EVIDENCE_REQUIRED_OUTSIDE_CI")
    stamped = time_utc(snapshot.get("observed_at"))
    need(stamped is not None
         and now - MAX_EVIDENCE_AGE <= stamped <= now + MAX_FUTURE_SKEW,
         "EVIDENCE_STALE_MISSING_OR_FUTURE")

    sha = identity.get("candidate_sha")
    need(isinstance(sha, str) and SHA.fullmatch(sha) is not None,
         "CANDIDATE_SHA_INVALID")
    need(identity.get("branch") == EXPECTED_BRANCH
         and identity.get("parent_branch") == EXPECTED_PARENT_BRANCH
         and identity.get("parent_sha") == PART6_SHA,
         "STACKED_PR_LINEAGE_MISMATCH")
    need(identity.get("main_sha") == MAIN_SHA
         and identity.get("deployed_api_sha") == MAIN_SHA,
         "MAIN_AND_PRODUCTION_DRIFT")
    need(exact_true(identity.get("draft"))
         and exact_false(identity.get("merged")),
         "PR_NOT_DRAFT_UNMERGED")

    need(ci.get("part5_sha") == PART5_SHA
         and type(ci.get("part5_passed_jobs")) is int
         and ci.get("part5_passed_jobs") == 7,
         "PART5_INTEGRATED_CI_MISSING")
    need(ci.get("part6_sha") == PART6_SHA
         and type(ci.get("part6_passed_jobs")) is int
         and ci.get("part6_passed_jobs") == 3,
         "PART6_RECOVERY_CI_MISSING")
    need(ci.get("candidate_sha") == sha and exact_true(ci.get("all_jobs_passed")),
         "CANDIDATE_CI_MISSING_OR_STALE")

    need(db.get("live_flyway") == "V99"
         and exact_true(db.get("live_history_readonly_verified")),
         "LIVE_FLYWAY_V99_UNVERIFIED")
    need(exact_true(db.get("migrations_v100_v103_immutable"))
         and exact_true(db.get("pg18_rehearsal"))
         and exact_true(db.get("tenant_rls_checked")),
         "MIGRATION_RLS_DRIFT_OR_UNVERIFIED")
    need(exact_false(db.get("production_migrations_applied")),
         "PRODUCTION_MIGRATION_FORBIDDEN_IN_OFFLINE_PHASE")

    need(exact_true(backup.get("fresh_dump"))
         and exact_true(backup.get("isolated_restore"))
         and exact_true(backup.get("encrypted_offsite_copy"))
         and exact_true(backup.get("offsite_restore_test"))
         and exact_true(backup.get("measured_rpo_rto")),
         "CURRENT_REAL_BACKUP_OR_OFFSITE_EVIDENCE_MISSING")

    need(exact_true(voice.get("carrier_owner_verified"))
         and exact_true(voice.get("sid_and_tenant_match"))
         and exact_true(voice.get("webhook_method_and_url_match"))
         and exact_true(voice.get("voice_model_config_verified")),
         "VOICE_OWNER_OR_MODEL_NOT_ATTESTED")

    need(exact_false(safety.get("real_call_enabled"))
         and exact_false(safety.get("outbound_enabled"))
         and exact_false(safety.get("gemini_external_enabled"))
         and exact_false(safety.get("twilio_mutations_enabled"))
         and exact_true(safety.get("other_tenants_isolated"))
         and exact_true(safety.get("pilot_paused")),
         "EXTERNAL_EFFECTS_OR_TENANT_SAFETY_FENCE_INVALID")

    approvals = human.get("approver_ids")
    valid_approvers = (isinstance(approvals, list) and len(approvals) == 2
                       and all(isinstance(v, str)
                               and re.fullmatch(r"reviewer-[a-z0-9-]{2,32}", v)
                               for v in approvals)
                       and approvals[0] != approvals[1])
    need(valid_approvers
         and exact_true(human.get("maintenance_window_documented"))
         and exact_true(human.get("rollback_operator_assigned"))
         and exact_true(human.get("stop_conditions_reviewed")),
         "HUMAN_ROLLOUT_AND_TWO_APPROVERS_UNVERIFIED")

    # Even an internally consistent, fresh and optimistic JSON is never
    # trusted as actual evidence, so the simulator NEVER returns GO.
    return {
        "part": "7/10",
        "state": "STOP" if blockers else "OFFLINE_REHEARSAL_ONLY",
        "blockers": sorted(set(blockers)),
        "source_trusted": False,
        "candidate_sha_format_ok": isinstance(sha, str)
        and SHA.fullmatch(sha) is not None,
        "external_attestation": "NOT_PROVEN_BY_INPUT",
        "prod_deploy": "NO-GO",
        "may_merge": False,
        "may_migrate": False,
        "may_modify_webhooks": False,
        "may_make_real_calls": False,
        "live_call_certified": False,
        "commercial_go": False,
    }


def fixture(now: datetime, *, optimistic: bool = False) -> dict:
    """Synthetic fixture: optimistic=True only tests the logical positive path."""
    return {
        "source": "synthetic",
        "observed_at": now.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        "identity": {
            "candidate_sha": "a" * 40,
            "branch": EXPECTED_BRANCH,
            "parent_branch": EXPECTED_PARENT_BRANCH,
            "parent_sha": PART6_SHA,
            "main_sha": MAIN_SHA,
            "deployed_api_sha": MAIN_SHA,
            "draft": True,
            "merged": False,
        },
        "ci": {
            "part5_sha": PART5_SHA, "part5_passed_jobs": 7,
            "part6_sha": PART6_SHA, "part6_passed_jobs": 3,
            "candidate_sha": "a" * 40, "all_jobs_passed": optimistic,
        },
        "database": {
            "live_flyway": "V99",
            "live_history_readonly_verified": optimistic,
            "migrations_v100_v103_immutable": True,
            "pg18_rehearsal": True, "tenant_rls_checked": optimistic,
            "production_migrations_applied": False,
        },
        "recovery": {
            "fresh_dump": optimistic, "isolated_restore": optimistic,
            "encrypted_offsite_copy": optimistic,
            "offsite_restore_test": optimistic, "measured_rpo_rto": optimistic,
        },
        "voice": {
            "carrier_owner_verified": optimistic, "sid_and_tenant_match": optimistic,
            "webhook_method_and_url_match": optimistic,
            "voice_model_config_verified": optimistic,
        },
        "safety": {
            "real_call_enabled": False, "outbound_enabled": False,
            "gemini_external_enabled": False, "twilio_mutations_enabled": False,
            "other_tenants_isolated": optimistic, "pilot_paused": True,
        },
        "human": {
            "approver_ids": ["reviewer-one", "reviewer-two"] if optimistic else [],
            "maintenance_window_documented": optimistic,
            "rollback_operator_assigned": optimistic,
            "stop_conditions_reviewed": optimistic,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--demo-blocked", action="store_true")
    mode.add_argument("--demo-optimistic", action="store_true")
    mode.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    now = datetime.now(timezone.utc)
    if args.evidence:
        try:
            doc = json.loads(args.evidence.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, ValueError):
            doc = {}
    else:
        doc = fixture(now, optimistic=args.demo_optimistic)
    result = assess(doc, now=now)
    # Output is strictly a fixed-schema redacted summary, never source content.
    print(json.dumps(result, sort_keys=True))
    if args.demo_blocked:
        return 0 if result["state"] == "STOP" else 2
    if args.demo_optimistic:
        return 0 if result["state"] == "OFFLINE_REHEARSAL_ONLY" else 2
    return 2 if result["state"] == "STOP" else 3


if __name__ == "__main__":
    raise SystemExit(main())
