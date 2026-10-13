"""Stage C fail-closed release safety contracts; no external network or credentials."""
import copy
import json
from pathlib import Path
import unittest

import stage_c_release_readiness as gate

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "docs/telephony/evidence/2026-10-13-stage-c-observed-no-go.json"


def baseline() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def synthetic_reviewable() -> dict:
    payload = baseline()
    payload["git"]["main_sha"] = gate.STAGE_B_SHA
    for pr in payload["git"]["pull_requests"]:
        pr["merged"] = True
        pr["draft"] = False
    payload["railway"]["deployed_git_sha"] = gate.STAGE_B_SHA
    payload["ci"] = {
        "head_sha": gate.STAGE_B_SHA,
        "conclusion": "success",
        "full_gate_jobs": 7,
        "passed_jobs": 7,
        "run_url": "https://github.com/Nicricht/helvoca/actions/runs/10000000000",
    }
    for f in ("fresh_backup_for_release_verified",
              "production_flyway_read_only_checked",
              "schema_backward_compatibility_checked",
              "independent_encrypted_backup_verified"):
        payload["postgres"][f] = True
    for f in payload["controls"]:
        payload["controls"][f] = True
    return payload


class StageCReleaseReadinessContractTest(unittest.TestCase):
    def test_actual_snapshot_is_no_go_with_explicit_reasons(self):
        result = gate.assess(baseline())
        self.assertEqual("BLOCKED", result.state)
        for reason in (
            "STACK_HAS_UNMERGED_PRS",
            "RAILWAY_DEPLOYED_SHA_UNVERIFIED",
            "RELEASE_CANDIDATE_NOT_ON_MAIN",
            "EXACT_SHA_SEVEN_GATE_CI_UNVERIFIED",
            "LIVE_FLYWAY_HISTORY_UNVERIFIED",
            "LIVE_CARRIER_OWNERSHIP_UNVERIFIED",
            "OFFSITE_ENCRYPTED_RECOVERY_UNVERIFIED",
        ):
            self.assertIn(reason, result.blockers)
        self.assertFalse(result.as_dict()["may_merge"])
        self.assertFalse(result.as_dict()["may_deploy"])
        self.assertFalse(result.as_dict()["may_run_live_call"])
        self.assertFalse(result.as_dict()["commercial_go"])

    def test_self_reported_perfect_snapshot_never_grants_permission(self):
        result = gate.assess(synthetic_reviewable())
        self.assertEqual("READY_FOR_INDEPENDENT_RELEASE_REVIEW", result.state)
        self.assertFalse(result.as_dict()["may_deploy"])
        self.assertFalse(result.as_dict()["may_merge"])
        self.assertTrue(result.as_dict()["independent_evidence_review_required"])

    def test_chain_base_tampering_blocks(self):
        data = synthetic_reviewable()
        data["git"]["pull_requests"][-1]["base"] = "main"
        self.assertIn("PR_CHAIN_BASE_OR_HEAD_MISMATCH", gate.assess(data).blockers)

    def test_chain_missing_pr_blocks(self):
        data = synthetic_reviewable()
        data["git"]["pull_requests"].pop()
        self.assertIn("PR_CHAIN_COUNT_MISMATCH", gate.assess(data).blockers)

    def test_unmerged_parent_blocks_even_if_other_gates_claim_true(self):
        data = synthetic_reviewable()
        data["git"]["pull_requests"][0]["merged"] = False
        self.assertIn("STACK_HAS_UNMERGED_PRS", gate.assess(data).blockers)

    def test_boolean_coercion_cannot_forge_ci_jobs(self):
        data = synthetic_reviewable()
        data["ci"]["full_gate_jobs"] = True
        self.assertIn("EXACT_SHA_SEVEN_GATE_CI_UNVERIFIED", gate.assess(data).blockers)

    def test_ci_on_different_commit_blocks(self):
        data = synthetic_reviewable()
        data["ci"]["head_sha"] = gate.MAIN_BEFORE_RELEASE
        self.assertIn("EXACT_SHA_SEVEN_GATE_CI_UNVERIFIED", gate.assess(data).blockers)

    def test_live_flyway_must_be_verified_separately(self):
        data = synthetic_reviewable()
        data["postgres"]["production_flyway_read_only_checked"] = False
        self.assertIn("LIVE_FLYWAY_HISTORY_UNVERIFIED", gate.assess(data).blockers)

    def test_backup_digest_and_volume_are_pinned(self):
        data = synthetic_reviewable()
        data["postgres"]["stage_a_archive_sha256"] = "0" * 64
        data["postgres"]["volume_id"] = "other"
        result = gate.assess(data)
        self.assertIn("DATABASE_VOLUME_CHANGED", result.blockers)
        self.assertIn("RECOVERY_AND_MIGRATION_REHEARSAL_NOT_VERIFIED", result.blockers)

    def test_unknown_production_main_blocks(self):
        data = synthetic_reviewable()
        data["git"]["main_sha"] = "a" * 40
        self.assertIn("PRODUCTION_MAIN_BASELINE_CHANGED", gate.assess(data).blockers)

    def test_carrier_identity_and_kill_switch_fail_closed(self):
        data = synthetic_reviewable()
        data["controls"]["carrier_twilio_ownership_checked"] = False
        data["controls"]["global_call_effects_disabled"] = False
        result = gate.assess(data)
        self.assertIn("LIVE_CARRIER_OWNERSHIP_UNVERIFIED", result.blockers)
        self.assertIn("VOICE_SAFETY_FENCE_UNVERIFIED", result.blockers)

    def test_operator_and_rollback_still_required(self):
        data = synthetic_reviewable()
        data["controls"]["rollback_operator_assigned"] = False
        data["postgres"]["schema_backward_compatibility_checked"] = False
        result = gate.assess(data)
        self.assertIn("HUMAN_ROLLOUT_PLAN_UNVERIFIED", result.blockers)
        self.assertIn("ROLLBACK_SCHEMA_COMPATIBILITY_UNVERIFIED", result.blockers)

    def test_invalid_input_fails_closed(self):
        self.assertEqual("INVALID_SNAPSHOT", gate.assess(None).blockers[0])
        self.assertEqual("BLOCKED", gate.assess([]).state)

    def test_pr_count_is_exact_and_805_not_a_parent(self):
        numbers = [pr["number"] for pr in baseline()["git"]["pull_requests"]]
        self.assertEqual([row[0] for row in gate.CHAIN], numbers)
        self.assertNotIn(805, numbers)


if __name__ == "__main__":
    unittest.main()
