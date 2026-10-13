#!/usr/bin/env python3
"""Helvoca Part 10 release handoff: immutable offline snapshot and STOP logic.

This is deliberately a PURE, NO-NETWORK auditor of observed GitHub evidence.
A PASS never authenticates current provider, DB, Railway or human evidence,
never makes a call and never authorizes a merge or deployment.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs/telephony/2026-10-13-voice-release-part10-manifest.json"
PARENT_SHA = "663ef0c54e02e0e86b828cd913c2aab4bc5b42da"
MAIN_AT_CAPTURE = "ca58693e167752bd6eab8ff4e9be770391fe4293"
EXPECTED_PHASES = (
    (811, "audit/voice-release-part1-lineage-20261013",
     "38aad94e8b2e0abe51885eab71203010ffe4b9c3"),
    (812, "test/voice-release-part2-pg18-upgrade-20261013",
     "6520ede7e072483f83cd0a70e2eb21cb186c87e6"),
    (813, "test/voice-release-part3-tenant-isolation-20261013",
     "25f45a898ec531d90d06a7e547e0a16f1f4fae3a"),
    (814, "test/voice-release-part4-twilio-reconciliation-20261013",
     "80094d6316ac92006c376cb7ab4514abe837bb6f"),
    (815, "test/voice-release-part5-integrated-offline-20261013",
     "5a78ec252a6133574dedc4b1b55df2c5d650b219"),
    (816, "test/voice-release-part6-recovery-offline-20261013",
     "8939fd4844eee83bcf74c71c7e0685de24f05f19"),
    (817, "test/voice-release-part7-predeployment-no-go-20261013",
     "0574feb80c8e4c20ab699f598f060fa57f5564af"),
    (818, "test/voice-release-part8-gemini-cost-consent-offline-20261013",
     "304aba39a7aed6932ce69af1a8a4c4a905e2ce44"),
    (819, "test/voice-release-part9-full-regression-offline-20261013",
     PARENT_SHA),
)
FIRST_PARENT_BRANCH = "test/voice-pilot-stage-d-carrier-db-readonly-20261013"
FIRST_PARENT_SHA = "fa58a1852499ce728554d20f76c1f4b7bb033267"
BRANCH10 = "test/voice-release-part10-final-handoff-no-go-20261013"
EXPECTED_BLOCKERS = {
    "current_flyway_version_readonly_verified",
    "production_backup_fresh_restored",
    "encrypted_independent_offsite_copy_restored",
    "representative_rpo_rto_measured",
    "twilio_account_pn_sid_tenant_provenance_reconciled",
    "twilio_current_webhook_verified",
    "gemini_api_key_project_and_model_entitlement_verified",
    "provider_pricing_and_enforceable_cost_limit_verified",
    "recording_consent_and_participant_approval_verified",
    "two_independent_operator_approvals_verified",
    "maintenance_window_and_rollback_signoff_verified",
}
EXPECTED_AUTHORIZATIONS = {
    "may_merge", "may_deploy", "may_migrate_production",
    "may_write_twilio", "may_update_webhooks",
    "may_open_billable_model_session", "may_make_real_calls",
    "may_launch_commercially",
}
FILES = {
    ".github/workflows/voice-release-part10-final-offline.yml",
    "docs/telephony/2026-10-13-voice-release-part10-final-handoff-no-go.md",
    "docs/telephony/2026-10-13-voice-release-part10-manifest.json",
    "scripts/ci/voice_part10_release_manifest.py",
    "scripts/ci/test_voice_part10_release_manifest.py",
}


def dictionary(doc: dict, key: str) -> dict:
    v = doc.get(key)
    return v if isinstance(v, dict) else {}


def exact_false(v: object) -> bool:
    return type(v) is bool and v is False


def exact_true(v: object) -> bool:
    return type(v) is bool and v is True


def is_sha(v: object) -> bool:
    return isinstance(v, str) and re.fullmatch(r"[a-f0-9]{40}", v) is not None


def assess_manifest(document: object) -> dict:
    """Pure check of STATIC, UNAUTHENTICATED snapshot consistency."""
    failures: list[str] = []

    def need(ok: bool, code: str) -> None:
        if not ok:
            failures.append(code)

    if not isinstance(document, dict):
        document = {}
        failures.append("MISSING_OR_MALFORMED_MANIFEST")

    need(document.get("schema") == "helvoca.voice.release-handoff.offline.v1"
         and document.get("repository") == "Nicricht/helvoca"
         and document.get("captured_on") == "2026-10-13"
         and document.get("classification") ==
         "OBSERVED_GITHUB_SNAPSHOT_NOT_LIVE_PROVIDER_ATTESTATION"
         and document.get("scope") == "PART10_OFFLINE_RELEASE_CANDIDATE",
         "UNEXPECTED_RELEASE_MANIFEST_IDENTITY")
    need(document.get("expected_main_sha_at_capture") == MAIN_AT_CAPTURE
         and document.get("root_parent_pr") == 810
         and document.get("root_parent_head_sha") == FIRST_PARENT_SHA,
         "MAIN_OR_ROOT_PARENT_DRIFT")

    stack = document.get("stack")
    if not isinstance(stack, list) or len(stack) != len(EXPECTED_PHASES):
        failures.append("MISSING_OR_REORDERED_PHASE_CHAIN")
        stack = []
    last_branch = FIRST_PARENT_BRANCH
    for phase, (pr, branch, sha) in enumerate(EXPECTED_PHASES, 1):
        item = stack[phase - 1] if len(stack) >= phase else None
        if not isinstance(item, dict):
            failures.append(f"PHASE_{phase}_MISSING")
            last_branch = branch
            continue
        need(type(item.get("phase")) is int and item.get("phase") == phase
             and type(item.get("pr")) is int and item.get("pr") == pr
             and item.get("branch") == branch and item.get("base_branch") == last_branch
             and is_sha(item.get("sha")) and item.get("sha") == sha,
             f"PHASE_{phase}_SHA_PR_OR_BASE_MISMATCH")
        need(item.get("observed_state") == "open"
             and exact_true(item.get("observed_draft"))
             and exact_false(item.get("observed_merged")),
             f"PHASE_{phase}_NOT_SNAPSHOT_DRAFT_UNMERGED")
        last_branch = branch
    ci = dictionary(document, "last_integrated_offline_ci")
    need(type(ci.get("phase")) is int and ci.get("phase") == 9
         and ci.get("candidate_sha") == PARENT_SHA
         and type(ci.get("push_run_id")) is int
         and ci.get("push_run_id") == 38378146552
         and type(ci.get("pr_run_id")) is int
         and ci.get("pr_run_id") == 38378187941
         and type(ci.get("expected_jobs_each")) is int
         and ci.get("expected_jobs_each") == 8
         and ci.get("push_conclusion") == "success"
         and ci.get("pr_conclusion") == "success",
         "PHASE9_CI_SNAPSHOT_MISMATCH")

    observed = dictionary(document, "observed_railway")
    need(observed.get("api") == "online"
         and observed.get("postgres") == "online"
         and observed.get("replicas_api") == "1/1"
         and observed.get("replicas_postgres") == "1/1"
         and observed.get("deployment_api_id") ==
         "4636d0d9-7be0-4a66-abce-58c68ccaede5"
         and exact_false(observed.get("production_data_accessed")),
         "PRODUCTION_STATUS_SNAPSHOT_CHANGED")

    blockers = dictionary(document, "release_blockers")
    need(set(blockers) == EXPECTED_BLOCKERS
         and all(exact_false(blockers.get(k)) for k in EXPECTED_BLOCKERS),
         "UNPROVEN_EXTERNAL_EVIDENCE_CANNOT_BE_CERTIFIED_BY_MANIFEST")
    authorizations = dictionary(document, "authorizations")
    need(set(authorizations) == EXPECTED_AUTHORIZATIONS
         and all(exact_false(authorizations.get(k)) for k in EXPECTED_AUTHORIZATIONS),
         "PRODUCTION_AUTHORIZATION_MUST_STAY_DENIED")
    need(document.get("production_go") == "NO-GO"
         and exact_false(document.get("live_call_certified"))
         and exact_false(document.get("commercial_go")),
         "NO_GO_STATUS_MUST_BE_EXPLICIT")
    disclaimer = document.get("disclaimer")
    need(isinstance(disclaimer, str)
         and "not authenticated" in disclaimer.lower()
         and "never authorizes production" in disclaimer.lower(),
         "SNAPSHOT_IS_NOT_ATTESTATION_DISCLAIMER_MISSING")

    return {
        "phase": "10/10",
        "result": "PASS_OFFLINE_PACKAGE" if not failures else "STOP",
        "blockers": sorted(set(failures)),
        "stacked_prs": len(EXPECTED_PHASES),
        "upstream_ci_jobs": "SNAPSHOT_ONLY_NOT_AUTHENTICATED",
        "external_evidence": "NOT_VERIFIED",
        "authoritative_live_checks_performed": False,
        "can_merge": False,
        "can_deploy": False,
        "can_migrate": False,
        "can_change_twilio": False,
        "can_make_real_calls": False,
        "production_go": "NO-GO",
        "live_call_certified": False,
        "commercial_go": False,
    }


def assess_diff(text: object) -> dict:
    """Inspect Git's name-status output: no modifications outside 5 new files."""
    failures = []
    if not isinstance(text, str):
        text = ""
        failures.append("DIFF_NOT_AVAILABLE")
    changes = [line for line in text.splitlines() if line]
    parsed = []
    for line in changes:
        parts = line.split("\t")
        if len(parts) != 2 or parts[0] != "A" or not parts[1].startswith((
                ".github/workflows/", "docs/telephony/", "scripts/ci/")):
            failures.append("MODIFIED_OR_UNEXPECTED_PATH")
        else:
            parsed.append(parts[1])
    if len(changes) != len(FILES) or len(parsed) != len(FILES) or set(parsed) != FILES:
        failures.append("EXACT_FIVE_RELEASE_FILES_REQUIRED")
    return {
        "result": "PASS_OFFLINE_FILES_ONLY" if not failures else "STOP",
        "blockers": sorted(set(failures)),
        "files_expected": len(FILES),
        "production_files_modified": "NOT_ALLOWED",
        "can_merge": False,
        "can_deploy": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changed-files", type=Path)
    args = parser.parse_args()
    try:
        document = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        document = None
    report = assess_manifest(document)
    print(json.dumps(report, sort_keys=True))
    if args.changed_files:
        try:
            diff_text = args.changed_files.read_text(encoding="utf-8")
        except (OSError, UnicodeError, ValueError):
            diff_text = None
        result = assess_diff(diff_text)
        print(json.dumps(result, sort_keys=True))
        if result["result"] != "PASS_OFFLINE_FILES_ONLY":
            return 2
    return 0 if report["result"] == "PASS_OFFLINE_PACKAGE" else 2


if __name__ == "__main__":
    raise SystemExit(main())
