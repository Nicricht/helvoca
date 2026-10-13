"""Part 7 read-only release decision model: adversarial STOP regression tests."""
from __future__ import annotations

import copy
from datetime import datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "part7_predeployment_gate", ROOT / "scripts/ci/voice_part7_predeployment_gate.py"
)
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


class OfflinePart7DecisionTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 13, 9, 0, tzinfo=timezone.utc)
        self.snapshot = gate.fixture(self.now, optimistic=True)

    def report(self, doc=None):
        return gate.assess(self.snapshot if doc is None else doc, now=self.now)

    def assert_blocked(self, code, doc=None):
        report = self.report(doc)
        self.assertEqual("STOP", report["state"], report)
        self.assertIn(code, report["blockers"], report)
        self.assertEqual("NO-GO", report["prod_deploy"])
        self.assertFalse(report["may_merge"])
        self.assertFalse(report["commercial_go"])

    def mutate(self, group, key, value):
        doc = copy.deepcopy(self.snapshot)
        doc[group][key] = value
        return doc

    def test_optimistic_fixture_is_never_a_release_authorization(self):
        r = self.report()
        self.assertEqual("OFFLINE_REHEARSAL_ONLY", r["state"])
        self.assertFalse(r["source_trusted"])
        self.assertEqual("NOT_PROVEN_BY_INPUT", r["external_attestation"])
        self.assertEqual([], r["blockers"])
        for key in ("may_merge", "may_migrate", "may_modify_webhooks",
                    "may_make_real_calls", "live_call_certified", "commercial_go"):
            self.assertFalse(r[key], key)

    def test_actual_incomplete_baseline_fails_closed(self):
        self.assertEqual("STOP", gate.assess(gate.fixture(self.now), now=self.now)["state"])

    def test_missing_and_malformed_snapshot(self):
        self.assert_blocked("INVALID_SNAPSHOT", [])
        self.assertEqual("STOP", gate.assess(None, now=self.now)["state"])
        self.assert_blocked("CANDIDATE_SHA_INVALID", {})
        self.assert_blocked("EVIDENCE_STALE_MISSING_OR_FUTURE", {})

    def test_real_or_untrusted_provenance_never_accepted(self):
        for value in ("production", "real", "test", None, True):
            with self.subTest(value=value):
                doc = copy.deepcopy(self.snapshot)
                doc["source"] = value
                self.assert_blocked(
                    "AUTHENTIC_EXTERNAL_EVIDENCE_REQUIRED_OUTSIDE_CI", doc)

    def test_old_future_or_nonutc_timestamp(self):
        for value in (
            (self.now - timedelta(hours=3)).isoformat().replace("+00:00", "Z"),
            (self.now + timedelta(minutes=6)).isoformat().replace("+00:00", "Z"),
            "2026-10-13T09:00:00+03:00", "not-a-date", None,
        ):
            with self.subTest(value=value):
                doc = copy.deepcopy(self.snapshot)
                doc["observed_at"] = value
                self.assert_blocked("EVIDENCE_STALE_MISSING_OR_FUTURE", doc)

    def test_stacked_branch_or_parent_drift(self):
        for key, value in (
            ("branch", "main"),
            ("parent_branch", "main"),
            ("parent_sha", "0" * 40),
        ):
            with self.subTest(key=key):
                self.assert_blocked(
                    "STACKED_PR_LINEAGE_MISMATCH", self.mutate("identity", key, value))

    def test_sha_validation_and_exact_ci_head(self):
        self.assert_blocked(
            "CANDIDATE_SHA_INVALID", self.mutate("identity", "candidate_sha", "abc"))
        self.assert_blocked(
            "CANDIDATE_CI_MISSING_OR_STALE",
            self.mutate("ci", "candidate_sha", "b" * 40))

    def test_deployed_main_hash_or_main_drift(self):
        for key in ("main_sha", "deployed_api_sha"):
            self.assert_blocked(
                "MAIN_AND_PRODUCTION_DRIFT",
                self.mutate("identity", key, "0" * 40))

    def test_pr_merged_and_not_draft(self):
        self.assert_blocked(
            "PR_NOT_DRAFT_UNMERGED",
            self.mutate("identity", "merged", True))
        self.assert_blocked(
            "PR_NOT_DRAFT_UNMERGED",
            self.mutate("identity", "draft", False))

    def test_prior_ci_sha_status_and_type_drift(self):
        for key, value, code in (
            ("part5_sha", "0" * 40, "PART5_INTEGRATED_CI_MISSING"),
            ("part5_passed_jobs", True, "PART5_INTEGRATED_CI_MISSING"),
            ("part5_passed_jobs", 6, "PART5_INTEGRATED_CI_MISSING"),
            ("part6_sha", "0" * 40, "PART6_RECOVERY_CI_MISSING"),
            ("part6_passed_jobs", 2, "PART6_RECOVERY_CI_MISSING"),
            ("part6_passed_jobs", True, "PART6_RECOVERY_CI_MISSING"),
            ("all_jobs_passed", 1, "CANDIDATE_CI_MISSING_OR_STALE"),
        ):
            with self.subTest(key=key, value=value):
                self.assert_blocked(code, self.mutate("ci", key, value))

    def test_live_flyway_history_and_migration_drift(self):
        for key, value, code in (
            ("live_flyway", "V103", "LIVE_FLYWAY_V99_UNVERIFIED"),
            ("live_history_readonly_verified", False, "LIVE_FLYWAY_V99_UNVERIFIED"),
            ("migrations_v100_v103_immutable", False, "MIGRATION_RLS_DRIFT_OR_UNVERIFIED"),
            ("pg18_rehearsal", False, "MIGRATION_RLS_DRIFT_OR_UNVERIFIED"),
            ("tenant_rls_checked", False, "MIGRATION_RLS_DRIFT_OR_UNVERIFIED"),
            ("production_migrations_applied", True, "PRODUCTION_MIGRATION_FORBIDDEN_IN_OFFLINE_PHASE"),
        ):
            with self.subTest(key=key):
                self.assert_blocked(code, self.mutate("database", key, value))

    def test_real_backup_offsite_restore_and_rpo_require_evidence(self):
        for key in ("fresh_dump", "isolated_restore", "encrypted_offsite_copy",
                    "offsite_restore_test", "measured_rpo_rto"):
            with self.subTest(key=key):
                self.assert_blocked(
                    "CURRENT_REAL_BACKUP_OR_OFFSITE_EVIDENCE_MISSING",
                    self.mutate("recovery", key, False))

    def test_carrier_tenant_webhook_and_model_require_attestation(self):
        for key in ("carrier_owner_verified", "sid_and_tenant_match",
                    "webhook_method_and_url_match", "voice_model_config_verified"):
            with self.subTest(key=key):
                self.assert_blocked(
                    "VOICE_OWNER_OR_MODEL_NOT_ATTESTED",
                    self.mutate("voice", key, False))

    def test_all_billable_external_switches_fail_closed(self):
        for key in ("real_call_enabled", "outbound_enabled",
                    "gemini_external_enabled", "twilio_mutations_enabled"):
            with self.subTest(key=key):
                self.assert_blocked(
                    "EXTERNAL_EFFECTS_OR_TENANT_SAFETY_FENCE_INVALID",
                    self.mutate("safety", key, True))

    def test_tenant_leak_and_resumed_pilot_block(self):
        self.assert_blocked(
            "EXTERNAL_EFFECTS_OR_TENANT_SAFETY_FENCE_INVALID",
            self.mutate("safety", "other_tenants_isolated", False))
        self.assert_blocked(
            "EXTERNAL_EFFECTS_OR_TENANT_SAFETY_FENCE_INVALID",
            self.mutate("safety", "pilot_paused", False))

    def test_human_approver_independence_and_operational_plan(self):
        for approvers in ([], ["reviewer-one"], ["reviewer-one", "reviewer-one"],
                          ["admin", "reviewer-two"], [True, "reviewer-two"]):
            with self.subTest(approvers=approvers):
                self.assert_blocked(
                    "HUMAN_ROLLOUT_AND_TWO_APPROVERS_UNVERIFIED",
                    self.mutate("human", "approver_ids", approvers))
        for key in ("maintenance_window_documented",
                    "rollback_operator_assigned", "stop_conditions_reviewed"):
            self.assert_blocked(
                "HUMAN_ROLLOUT_AND_TWO_APPROVERS_UNVERIFIED",
                self.mutate("human", key, False))

    def test_no_snapshot_data_leak_to_result(self):
        doc = copy.deepcopy(self.snapshot)
        secret = "PRIVATE_DO_NOT_LOG_123"
        doc["unused_note"] = secret
        doc["voice"]["raw_provider_sid"] = secret
        self.assertNotIn(secret, str(self.report(doc)))

    def test_no_network_or_prod_mutation_code_in_offline_module(self):
        source = gate.__file__ and Path(gate.__file__).read_text(encoding="utf-8")
        for forbidden in (
            "import requests", "import subprocess", "urllib.request",
            "TWILIO_AUTH_TOKEN", "DATABASE_URL", "railway ssh",
            "flyway migrate", "pg_restore -h", "send_sms(", "requests.post(",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
