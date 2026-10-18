"""Stage 1/10 Helvoca PR lineage fail-closed unit tests, entirely offline."""
from __future__ import annotations

from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/voice_part1_stacked_pr_lineage_audit.py"
SNAPSHOT = ROOT / "docs/telephony/evidence/2026-10-13-part1-pr-lineage-readonly-snapshot.json"
SPEC = importlib.util.spec_from_file_location("voice_stage1_audit", SCRIPT)
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


def observed():
    return json.loads(SNAPSHOT.read_text(encoding="utf-8"))


class Part1ReleaseAuditContracts(unittest.TestCase):
    def test_observed_chain_passes_but_never_authorizes_release(self):
        result = AUDIT.assess(observed())
        self.assertEqual("PASS_OFFLINE_SNAPSHOT", result["integrity"])
        self.assertEqual([], result["integrity_errors"])
        self.assertEqual("NO_GO", result["release"])
        self.assertTrue(result["current_truth_recheck_required"])
        for flag in ("may_merge", "may_deploy", "may_run_live_call", "commercial_go"):
            self.assertFalse(result[flag])

    def test_chain_exact_order_has_no_missing_or_parallel_prs(self):
        chain = observed()["ordered_chain"]
        self.assertEqual([*range(792, 805), *range(806, 811)],
                         [x["number"] for x in chain])
        self.assertNotIn(791, [x["number"] for x in chain])
        self.assertNotIn(805, [x["number"] for x in chain])
        self.assertTrue(all(x["draft"] and x["state"] == "open"
                            and not x["merged"] for x in chain))
        self.assertEqual("main", chain[0]["base"])
        for earlier, later in zip(chain, chain[1:]):
            self.assertEqual(earlier["head"], later["base"])

    def test_candidate_sha_mutation_fails(self):
        data = observed()
        data["observed_baseline"]["candidate_sha"] = "0" * 40
        self.assertIn("CANDIDATE_SHA_CHANGED", AUDIT.assess(data)["integrity_errors"])

    def test_main_commit_change_fails(self):
        data = observed()
        data["observed_baseline"]["main_sha"] = "0" * 40
        self.assertIn("MAIN_DEPLOY_SHA_DIVERGENCE", AUDIT.assess(data)["integrity_errors"])

    def test_candidate_missing_main_ancestry_fails(self):
        data = observed()
        data["observed_baseline"]["candidate_compare_to_main"]["behind_by"] = 1
        self.assertIn("MAIN_CANDIDATE_ANCESTRY_UNVERIFIED",
                      AUDIT.assess(data)["integrity_errors"])

    def test_root_791_ancestor_is_not_separate_release_package(self):
        data = observed()
        data["root_candidate"]["ancestor_of_pr_792_head"] = False
        self.assertIn("PR791_NOT_CERTIFIED_ANCESTOR_OF_PR792",
                      AUDIT.assess(data)["integrity_errors"])

    def test_parallel_805_requires_disposition(self):
        data = observed()
        data["parallel_805"]["review_required"] = False
        self.assertIn("PARALLEL_805_NOT_ACCOUNTED_FOR",
                      AUDIT.assess(data)["integrity_errors"])

    def test_parallel_805_cannot_be_inserted_automatically(self):
        data = observed()
        data["ordered_chain"].insert(13, {
            "number": 805, "head": "parallel-branch", "base": "main",
            "head_sha": "0" * 40, "draft": True, "merged": False,
            "state": "open", "mergeable": True,
        })
        result = AUDIT.assess(data)
        self.assertEqual("BLOCKED", result["integrity"])
        self.assertIn("CHAIN_LENGTH_MISMATCH", result["integrity_errors"])
        self.assertIn("PARALLEL_OR_ROOT_INSERTED_IN_CHAIN",
                      result["integrity_errors"])

    def test_changed_branch_base_is_blocked(self):
        data = observed()
        data["ordered_chain"][7]["base"] = "main"
        self.assertIn("CHAIN_LINK_INVALID_PR_799",
                      AUDIT.assess(data)["integrity_errors"])

    def test_any_closed_or_merged_pr_is_stale_snapshot(self):
        data = observed()
        data["ordered_chain"][3]["merged"] = True
        self.assertIn("CHAIN_PR_STATUS_CHANGED_795",
                      AUDIT.assess(data)["integrity_errors"])

    def test_changed_pr_sha_is_blocked(self):
        data = observed()
        data["ordered_chain"][-1]["head_sha"] = "0" * 40
        self.assertIn("CHAIN_LINK_INVALID_PR_810",
                      AUDIT.assess(data)["integrity_errors"])

    def test_ci_on_old_head_cannot_certify_latest(self):
        data = observed()
        data["ci_evidence"]["candidate_exact_sha_7_gate_internal_certified"] = True
        result = AUDIT.assess(data)
        self.assertIn("CI_RELEASE_SCOPE_OVERCLAIMED", result["integrity_errors"])
        self.assertEqual("NO_GO", result["release"])

    def test_offline_specialized_ci_falsely_replaced_with_7job_fails(self):
        data = observed()
        data["ci_evidence"]["stage_d_candidate_specialized_18_of_18"]["run_id"] = 38348597042
        self.assertIn("LATEST_SPECIALIZED_CI_EVIDENCE_MISMATCH",
                      AUDIT.assess(data)["integrity_errors"])

    def test_no_go_flags_enforced_individually(self):
        for name in AUDIT.SAFETY_DENIALS:
            with self.subTest(flag=name):
                data = observed()
                data["safety_gates"][name] = True
                result = AUDIT.assess(data)
                self.assertEqual("NO_GO", result["release"])
                self.assertIn("UNAUTHORIZED_RELEASE_FLAG_" + name.upper(),
                              result["integrity_errors"])

    def test_unknown_snapshot_rejected_without_exceptions(self):
        for value in (None, "invalid", 5, [], {}, {"schema": "bad"}):
            with self.subTest(value=value):
                result = AUDIT.assess(value)
                self.assertEqual("BLOCKED", result["integrity"])
                self.assertEqual("NO_GO", result["release"])

    def test_cli_expected_snapshot_reports_pass_without_go(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "copy.json"
            target.write_text(json.dumps(observed()), encoding="utf-8")
            # The offline CLI must never reach the network or execute a command.
            self.assertEqual(0, AUDIT.main(["--snapshot", str(target)]))

    def test_actual_snapshot_does_not_publish_telco_identifiers(self):
        raw = SNAPSHOT.read_text(encoding="utf-8")
        self.assertNotIn("+14355652512", raw)
        self.assertNotIn("044a5d73-2df7-4e77-849f-8639d05ac30b", raw)
        self.assertNotIn("TWILIO_AUTH_TOKEN", raw)
        self.assertNotIn("PGPASSWORD", raw)


if __name__ == "__main__":
    unittest.main()
