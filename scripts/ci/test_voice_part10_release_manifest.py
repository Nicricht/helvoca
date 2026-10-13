"""Part 10 final NO-GO release packet: adversarial immutable snapshot tests."""
from __future__ import annotations

from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "helvoca_part10_manifest", ROOT / "scripts/ci/voice_part10_release_manifest.py"
)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class FinalReleaseManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = json.loads(gate.MANIFEST.read_text(encoding="utf-8"))

    def changed(self, *path):
        data = deepcopy(self.source)
        obj = data
        for key in path[:-2]:
            obj = obj[key]
        obj[path[-2]] = path[-1]
        return data

    def blocked(self, data, code):
        r = gate.assess_manifest(data)
        self.assertEqual("STOP", r["result"], r)
        self.assertIn(code, r["blockers"])
        self.assertEqual("NO-GO", r["production_go"])
        self.assertFalse(r["can_merge"])
        self.assertFalse(r["can_make_real_calls"])
        self.assertFalse(r["commercial_go"])

    def test_original_is_only_offline_and_not_an_authorization(self):
        r = gate.assess_manifest(self.source)
        self.assertEqual("PASS_OFFLINE_PACKAGE", r["result"], r["blockers"])
        self.assertEqual(9, r["stacked_prs"])
        self.assertEqual("SNAPSHOT_ONLY_NOT_AUTHENTICATED", r["upstream_ci_jobs"])
        self.assertEqual("NOT_VERIFIED", r["external_evidence"])
        self.assertFalse(r["authoritative_live_checks_performed"])
        for field in ("can_merge", "can_deploy", "can_migrate",
                      "can_change_twilio", "can_make_real_calls",
                      "live_call_certified", "commercial_go"):
            self.assertFalse(r[field])

    def test_malformed_or_missing_input_fails_closed(self):
        for data in (None, [], {}, "", True, 2):
            self.blocked(data, "UNEXPECTED_RELEASE_MANIFEST_IDENTITY")

    def test_wrong_repository_scope_or_capture_date(self):
        for key, value in (
            ("schema", "release.live.v1"), ("repository", "other/repo"),
            ("captured_on", "2026-10-12"), ("scope", "PRODUCTION_GO"),
            ("classification", "SIGNED_PROVIDER_ATTESTATION"),
        ):
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d[key] = value
                self.blocked(d, "UNEXPECTED_RELEASE_MANIFEST_IDENTITY")

    def test_main_branch_or_root_parent_drift(self):
        for key, value in (
            ("expected_main_sha_at_capture", "0" * 40),
            ("root_parent_pr", 819),
            ("root_parent_head_sha", "0" * 40),
        ):
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d[key] = value
                self.blocked(d, "MAIN_OR_ROOT_PARENT_DRIFT")

    def test_missing_extra_reordered_phases(self):
        for data in (
            self.source["stack"][:-1],
            self.source["stack"] + [self.source["stack"][0]],
            list(reversed(self.source["stack"])),
        ):
            d = deepcopy(self.source)
            d["stack"] = data
            self.assertEqual("STOP", gate.assess_manifest(d)["result"])

    def test_all_pr_phase_names_shas_and_bases_are_pinned(self):
        for idx in range(9):
            for key, value in (
                ("phase", True), ("pr", True),
                ("branch", "main"), ("base_branch", "main"),
                ("sha", "a" * 40),
            ):
                with self.subTest(phase=idx+1, key=key):
                    d = deepcopy(self.source)
                    d["stack"][idx][key] = value
                    self.blocked(d, f"PHASE_{idx+1}_SHA_PR_OR_BASE_MISMATCH")

    def test_none_or_invalid_sha_blocks(self):
        for value in (None, "Z" * 40, "0" * 39, " " + "0" * 40):
            d = deepcopy(self.source)
            d["stack"][8]["sha"] = value
            self.blocked(d, "PHASE_9_SHA_PR_OR_BASE_MISMATCH")

    def test_observed_draft_open_not_merged_required_each_phase(self):
        for idx in range(9):
            for key, value in (
                ("observed_state", "merged"),
                ("observed_draft", False),
                ("observed_merged", True),
                ("observed_draft", 1),
                ("observed_merged", 0),
            ):
                with self.subTest(phase=idx+1,key=key):
                    d = deepcopy(self.source)
                    d["stack"][idx][key] = value
                    self.blocked(d, f"PHASE_{idx+1}_NOT_SNAPSHOT_DRAFT_UNMERGED")

    def test_no_false_upstream_ci_success(self):
        for key, value in (
            ("phase", 10), ("candidate_sha", "0" * 40),
            ("push_run_id", 123), ("pr_run_id", 123),
            ("expected_jobs_each", 7),
            ("push_conclusion", "in_progress"), ("pr_conclusion", "failure"),
            ("push_run_id", True), ("pr_run_id", True),
            ("expected_jobs_each", True),
        ):
            with self.subTest(key=key,value=value):
                d = deepcopy(self.source)
                d["last_integrated_offline_ci"][key] = value
                self.blocked(d, "PHASE9_CI_SNAPSHOT_MISMATCH")

    def test_railway_operational_snapshot_not_rewritten(self):
        for key, value in (
            ("api", "crashed"), ("postgres", "unknown"),
            ("replicas_api", "2/2"), ("replicas_postgres", "0/1"),
            ("deployment_api_id", "0" * 36),
            ("production_data_accessed", True),
        ):
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d["observed_railway"][key] = value
                self.blocked(d, "PRODUCTION_STATUS_SNAPSHOT_CHANGED")

    def test_every_unverified_external_gate_stays_false(self):
        for key in gate.EXPECTED_BLOCKERS:
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d["release_blockers"][key] = True
                self.blocked(d, "UNPROVEN_EXTERNAL_EVIDENCE_CANNOT_BE_CERTIFIED_BY_MANIFEST")
                d["release_blockers"][key] = 0
                self.blocked(d, "UNPROVEN_EXTERNAL_EVIDENCE_CANNOT_BE_CERTIFIED_BY_MANIFEST")

    def test_added_or_deleted_blocker_is_rejected(self):
        for action in ("add", "delete"):
            d = deepcopy(self.source)
            if action == "add":
                d["release_blockers"]["approval_from_json"] = False
            else:
                d["release_blockers"].pop(next(iter(gate.EXPECTED_BLOCKERS)))
            self.blocked(d, "UNPROVEN_EXTERNAL_EVIDENCE_CANNOT_BE_CERTIFIED_BY_MANIFEST")

    def test_all_release_permissions_must_remain_false_exact_booleans(self):
        for key in gate.EXPECTED_AUTHORIZATIONS:
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d["authorizations"][key] = True
                self.blocked(d, "PRODUCTION_AUTHORIZATION_MUST_STAY_DENIED")
                d["authorizations"][key] = 0
                self.blocked(d, "PRODUCTION_AUTHORIZATION_MUST_STAY_DENIED")

    def test_no_go_is_explicit_and_boolean(self):
        for key, value in (
            ("production_go", "GO"),
            ("production_go", None),
            ("live_call_certified", True),
            ("commercial_go", True),
            ("live_call_certified", 0),
            ("commercial_go", 0),
        ):
            with self.subTest(key=key):
                d = deepcopy(self.source)
                d[key] = value
                self.blocked(d, "NO_GO_STATUS_MUST_BE_EXPLICIT")

    def test_disclaimer_cannot_be_removed_or_upgrade_observation_to_attestation(self):
        for value in ("", "All certified", None):
            d = deepcopy(self.source)
            d["disclaimer"] = value
            self.blocked(d, "SNAPSHOT_IS_NOT_ATTESTATION_DISCLAIMER_MISSING")

    def test_personal_tokens_sids_and_numbers_not_in_manifest_values(self):
        data = json.dumps(self.source, ensure_ascii=False)
        for forbidden in (
            "TWILIO_AUTH_TOKEN=", "GEMINI_API_KEY=", "DB_PASSWORD=",
            "+14355652512", "sk-proj-", "AC" + "0" * 32, "PN" + "0" * 32
        ):
            self.assertNotIn(forbidden, data)

    def test_exact_new_release_file_list_is_required(self):
        correct = "\n".join("A\t" + path for path in sorted(gate.FILES)) + "\n"
        r = gate.assess_diff(correct)
        self.assertEqual("PASS_OFFLINE_FILES_ONLY", r["result"])
        self.assertFalse(r["can_merge"])
        self.assertFalse(r["can_deploy"])
        self.assertEqual(5, r["files_expected"])

    def test_diff_cannot_change_business_logic_migrations_or_credentials(self):
        correct = "\n".join("A\t" + path for path in sorted(gate.FILES)) + "\n"
        for line in (
            "M\tsrc/main/resources/application.yml",
            "A\tsrc/main/java/cl/helvoca/phone/TwilioWrite.java",
            "M\tsrc/main/resources/db/migration/V100__danger.sql",
            "A\t.github/workflows/deploy-prod.yml",
            "D\tsrc/main/java/cl/helvoca/voice/VoiceProvider.java",
            "R100\told\tnew",
        ):
            with self.subTest(line=line):
                self.assertEqual("STOP", gate.assess_diff(correct + line + "\n")["result"])
        self.assertEqual("STOP", gate.assess_diff(correct.replace("A\t", "M\t", 1))["result"])

    def test_missing_extra_duplicate_and_non_string_file_lists_fail(self):
        paths = sorted(gate.FILES)
        good = "\n".join(f"A\t{x}" for x in paths) + "\n"
        for value in ("", None, good.replace("A\t" + paths[0] + "\n", ""),
                      good + "A\t" + paths[0] + "\n"):
            with self.subTest(value=str(value)[:32]):
                self.assertEqual("STOP", gate.assess_diff(value)["result"])


if __name__ == "__main__":
    unittest.main()
