#!/usr/bin/env python3
"""Part 1/10: immutable, OFFLINE validation of one observed GitHub/Railway snapshot.

Does not authenticate to GitHub, Railway, Twilio or PostgreSQL; never merges,
deploys, executes SQL, starts a call, accesses backups or reads environment
secrets. A successful audit means the snapshot is structurally consistent,
NOT that release is authorized or current production has been rechecked.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

MAIN_SHA = "ca58693e167752bd6eab8ff4e9be770391fe4293"
ROOT_791_SHA = "7851f1f0868ee8a84634ab0fe08063b41b55dd24"
PARALLEL_805_SHA = "42b045623e0b2985521d2236ee0137433b09c352"
CANDIDATE_SHA = "fa58a1852499ce728554d20f76c1f4b7bb033267"
STAGE_C_SHA = "5e75cc87f824312b363e89c24cf269677677e6de"
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
CHAIN = (
    (792, "integration/voice-main-phase2-candidate-20261012", "85be445e86ddefb15e58a4904d09e002cb1150e9"),
    (793, "audit/twilio-railway-offline-phase3-20261012", "161cfb8f15a25b30c2aaa8e06ba7a2e8cad7cb6e"),
    (794, "feat/twilio-verified-existing-voice-route-phase4-20261012", "abb37ed7b81cb1e885cfe8a0bfa9820f1522f917"),
    (795, "feat/gemini-live-offline-session-config-phase5-20261012", "c9a233a5336cc0df50ba35302941929c3f17b235"),
    (796, "test/voice-cross-tenant-postgres-phase6-20261012", "c88eea19f07b361c0a00f7d77d258d68db9db29a"),
    (797, "test/voice-offline-lifecycle-phase7-20261012", "73f94adcdf9a416c6cfd0ed016a4c91fe77d5cdd"),
    (798, "test/voice-final-certification-phase8-20261012", "d95c16951c3fde1f18082b86404d85c214c60320"),
    (799, "feat/voice-real-pilot-1of6-preflight-20261012", "82335652263599eeca4ba5ee55d9d1186cfb690c"),
    (800, "feat/voice-real-pilot-2of6-carrier-binding-20261012", "522b4cd9eaae8edc1e2ebf3fba78dda50c0ed70e"),
    (801, "feat/voice-real-pilot-3of6-gemini-audio-20261012", "8b13ea90ca6dff50f47a094825e4a7c096c8aa9e"),
    (802, "test/voice-real-pilot-4of6-integrated-release-20261012", "92f430ba92b772e0109bdb2f4a337ddbfc463d33"),
    (803, "chore/voice-real-pilot-5of6-production-gate-20261012", "738d0f9f6055b74db1894d7c323e1bc18de623ae"),
    (804, "test/voice-real-pilot-6of6-live-evidence-20261012", "783bef015d3edc2030ab63f003c8806dd9959bd6"),
    (806, "chore/voice-pilot-post6-railway-recovery-audit-20261013", "06ed0b2dc9a41baf2b8a02f81725dd80ce6467a9"),
    (807, "test/voice-pilot-stage-b-pg18-migration-rehearsal-20261013", "9c1810457468fc2be8ab8cfe105af8c7448ac324"),
    (808, "chore/voice-pilot-stage-c-release-readiness-20261013", STAGE_C_SHA),
    (809, "chore/voice-pilot-stage-d-operational-preflight-20261013", "e398d1e892ce534bdca250012f7a96c94948e705"),
    (810, "test/voice-pilot-stage-d-carrier-db-readonly-20261013", CANDIDATE_SHA),
)
SAFETY_DENIALS = ("may_merge", "may_deploy", "may_execute_migrations",
                  "may_call", "commercial_go")
RELEASE_BLOCKERS = (
    "EXACT_CANDIDATE_FULL_SAFE_CI_PENDING",
    "PARALLEL_PR805_DECISION_PENDING",
    "PRODUCTION_CHANGE_AUTHORIZATION_MISSING",
    "TWILIO_DB_PROVENANCE_BLOCKED_ON_V99",
    "LIVE_CALL_EVIDENCE_PENDING",
)


def section(doc, name):
    value = doc.get(name) if isinstance(doc, dict) else None
    return value if isinstance(value, dict) else {}


def assess(doc) -> dict:
    failures = []

    def require(ok, code):
        if not ok:
            failures.append(code)

    require(isinstance(doc, dict) and doc.get("schema")
            == "helvoca.part1.readonly-lineage-snapshot.v1", "INVALID_SNAPSHOT_SCHEMA")
    require(isinstance(doc, dict) and doc.get("repository") == "Nicricht/helvoca",
            "REPOSITORY_MISMATCH")

    baseline = section(doc, "observed_baseline")
    require(baseline.get("main_sha") == MAIN_SHA
            and baseline.get("deployed_branch") == "main"
            and baseline.get("railway_api_deploy_sha") == MAIN_SHA,
            "MAIN_DEPLOY_SHA_DIVERGENCE")
    compare = section(baseline, "candidate_compare_to_main")
    require(compare.get("status") == "ahead"
            and compare.get("ahead_by") == 300
            and compare.get("behind_by") == 0
            and compare.get("files_changed") == 173,
            "MAIN_CANDIDATE_ANCESTRY_UNVERIFIED")
    require(baseline.get("candidate_pr") == 810
            and baseline.get("candidate_sha") == CANDIDATE_SHA,
            "CANDIDATE_SHA_CHANGED")
    require(baseline.get("railway_api_online") is True
            and baseline.get("railway_postgres_online") is True
            and baseline.get("railway_pending_work") == 0,
            "PRODUCTION_STATUS_DRIFT")
    require(baseline.get("production_flyway_operator_reported") == "V99"
            and baseline.get("twilio_carrier_db_operator_reported")
            == "BLOCKED_V99_PROVENANCE",
            "PRODUCTION_EVIDENCE_DRIFT")

    root = section(doc, "root_candidate")
    root_compare = section(root, "comparison_791_to_792")
    require(root.get("pr") == 791 and root.get("head_sha") == ROOT_791_SHA
            and root.get("base") == "main" and root.get("draft") is True
            and root.get("ancestor_of_pr_792_head") is True
            and root_compare.get("status") == "ahead"
            and root_compare.get("behind_by") == 0
            and root_compare.get("ahead_by") == 109,
            "PR791_NOT_CERTIFIED_ANCESTOR_OF_PR792")

    ordered = doc.get("ordered_chain") if isinstance(doc, dict) else None
    require(isinstance(ordered, list) and len(ordered) == len(CHAIN),
            "CHAIN_LENGTH_MISMATCH")
    if isinstance(ordered, list):
        for i, (num, branch, sha) in enumerate(CHAIN):
            entry = ordered[i] if len(ordered) > i and isinstance(ordered[i], dict) else {}
            base = "main" if i == 0 else CHAIN[i - 1][1]
            require(entry.get("number") == num and entry.get("head") == branch
                    and entry.get("base") == base
                    and entry.get("head_sha") == sha
                    and isinstance(entry.get("head_sha"), str)
                    and bool(SHA_PATTERN.fullmatch(entry["head_sha"])),
                    "CHAIN_LINK_INVALID_PR_" + str(num))
            require(entry.get("state") == "open" and entry.get("draft") is True
                    and entry.get("merged") is False and entry.get("mergeable") is True,
                    "CHAIN_PR_STATUS_CHANGED_" + str(num))
        require(all(x.get("number") not in (791, 805) for x in ordered
                    if isinstance(x, dict)), "PARALLEL_OR_ROOT_INSERTED_IN_CHAIN")

    parallel = section(doc, "parallel_805")
    divergence = section(parallel, "comparison_805_to_810")
    require(parallel.get("pr") == 805
            and parallel.get("head_sha") == PARALLEL_805_SHA
            and parallel.get("state") == "open"
            and parallel.get("draft") is True
            and parallel.get("review_required") is True
            and divergence.get("status") == "diverged"
            and divergence.get("candidate_ahead") == 69
            and divergence.get("candidate_behind") == 4,
            "PARALLEL_805_NOT_ACCOUNTED_FOR")

    ci = section(doc, "ci_evidence")
    stage_c = section(ci, "stage_c_full_safe_internal_7_of_7")
    stage_d = section(ci, "stage_d_candidate_specialized_18_of_18")
    require(stage_c.get("head_sha") == STAGE_C_SHA
            and stage_c.get("run_id") == 38348597042
            and stage_c.get("status") == "completed"
            and stage_c.get("conclusion") == "success"
            and stage_c.get("jobs") == 7,
            "OLD_7_JOB_CI_EVIDENCE_MISMATCH")
    require(stage_d.get("head_sha") == CANDIDATE_SHA
            and stage_d.get("run_id") == 38354260073
            and stage_d.get("status") == "completed"
            and stage_d.get("conclusion") == "success"
            and stage_d.get("tests") == 18,
            "LATEST_SPECIALIZED_CI_EVIDENCE_MISMATCH")
    require(ci.get("candidate_exact_sha_7_gate_internal_certified") is False
            and ci.get("external_public_smoke_allowed") is False,
            "CI_RELEASE_SCOPE_OVERCLAIMED")

    gates = section(doc, "safety_gates")
    for name in SAFETY_DENIALS:
        require(gates.get(name) is False, "UNAUTHORIZED_RELEASE_FLAG_" + name.upper())
    require(gates.get("need_explicit_operator_approval") is True,
            "HUMAN_APPROVAL_GATE_MISSING")

    return {
        "part": "1/10",
        "integrity": "PASS_OFFLINE_SNAPSHOT" if not failures else "BLOCKED",
        "integrity_errors": sorted(set(failures)),
        "release": "NO_GO",
        "release_blockers": list(RELEASE_BLOCKERS),
        "may_merge": False,
        "may_deploy": False,
        "may_run_live_call": False,
        "commercial_go": False,
        "current_truth_recheck_required": True,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        snapshot = json.loads(args.snapshot.read_text(encoding="utf-8"))
        result = assess(snapshot)
    except (OSError, UnicodeError, ValueError):
        result = assess(None)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["integrity"] == "PASS_OFFLINE_SNAPSHOT" else 2


if __name__ == "__main__":
    raise SystemExit(main())
