"""Safety regression checks for the Phase 5 production pre-deploy gate."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from voice_pilot_phase5_gate import BASELINE_MAIN_SHA, EXPECTED_MIGRATIONS, check

EVIDENCE_PATH = Path(__file__).resolve().parents[2] / (
    "docs/telephony/evidence/2026-10-12-phase5-production-no-go.json"
)


def nominal():
    """Synthetic full evidence is permitted only within offline unit tests."""
    return {
        "baseline": {
            "service_name": "helvoca-api",
            "production_commit": BASELINE_MAIN_SHA,
            "production_branch": "main",
            "staged_changes": 0,
        },
        "release": {
            "candidate_commit": "1" * 40,
            "full_ci_sha": "1" * 40,
            "full_ci_conclusion": "success",
            "full_ci_job_count": 7,
            "full_ci_successful_jobs": 7,
            "approved_change_scope": "voice-pilot-one-tenant",
            "maintenance_window_confirmed": True,
            "rollback_baseline_commit": BASELINE_MAIN_SHA,
        },
        "postgres": {
            "service_name": "Postgres",
            "volume_id": "b28c1046-168e-4f2e-bf02-18a2a2f1e611",
            "migrations_to_apply": list(EXPECTED_MIGRATIONS),
            "migration_compatibility_reviewed": True,
            "flyway_history_read_only_confirmed": True,
            "backup": {
                "backup_id": "synthetic-snapshot-test",
                "snapshot_freshness_verified": True,
                "restore_evidence_id": "synthetic-restore-test",
                "isolated_restore_completed": True,
                "source_volume_untouched": True,
            },
        },
        "safeguards": {
            "global_pilot_effects_disabled": True,
            "other_tenants_unchanged": True,
            "health_rollback_operator_assigned": True,
        },
    }


class Phase5ProductionGateTest(unittest.TestCase):
    def test_real_current_evidence_is_blocked_and_not_commercially_approved(self):
        data = json.loads(EVIDENCE_PATH.read_text(encoding="utf-8"))
        result = check(data)
        self.assertEqual("BLOCKED", result.state)
        self.assertIn("FRESH_BACKUP_NOT_VERIFIED", result.blockers)
        self.assertIn("ISOLATED_RESTORE_NOT_CERTIFIED", result.blockers)
        self.assertIn("CANDIDATE_FULL_CI_NOT_CERTIFIED", result.blockers)
        self.assertFalse(result.live_call_certified)
        self.assertFalse(result.commercial_go)
        self.assertFalse(result.as_dict()["is_deployment_authorization"])

    def test_nominal_evidence_only_yields_human_review_not_deployment_authorization(self):
        result = check(nominal())
        self.assertEqual("READY_FOR_HUMAN_DEPLOYMENT_REVIEW", result.state)
        self.assertFalse(result.live_call_certified)
        self.assertFalse(result.commercial_go)
        self.assertFalse(result.as_dict()["is_deployment_authorization"])

    def test_absent_or_invalid_evidence_fails_closed(self):
        for payload in [None, {}, [], "ready", {"baseline": {}}]:
            result = check(payload)
            self.assertEqual("BLOCKED", result.state)

    def test_corrupt_nested_sections_are_blocked_without_throwing(self):
        for section in ("baseline", "release", "postgres", "safeguards"):
            with self.subTest(section=section):
                data = nominal()
                data[section] = ["untrusted"]
                self.assertEqual("BLOCKED", check(data).state)
        data = nominal()
        data["postgres"]["backup"] = "untrusted"
        self.assertIn("FRESH_BACKUP_NOT_VERIFIED", check(data).blockers)

    def test_each_critical_production_requirement_is_independently_required(self):
        cases = [
            (("baseline", "production_commit"), "a" * 40, "PRODUCTION_BASELINE_CHANGED"),
            (("baseline", "staged_changes"), 1, "STAGED_PRODUCTION_CHANGES"),
            (("release", "candidate_commit"), BASELINE_MAIN_SHA, "CANDIDATE_COMMIT_NOT_PINNED"),
            (("release", "full_ci_sha"), "2" * 40, "CANDIDATE_FULL_CI_NOT_CERTIFIED"),
            (("release", "full_ci_successful_jobs"), 6, "CANDIDATE_FULL_CI_NOT_CERTIFIED"),
            (("release", "approved_change_scope"), "all-tenants", "RELEASE_SCOPE_NOT_PINNED"),
            (("release", "maintenance_window_confirmed"), False, "MAINTENANCE_WINDOW_NOT_CONFIRMED"),
            (("release", "rollback_baseline_commit"), "2" * 40, "ROLLBACK_CODE_BASELINE_NOT_PINNED"),
            (("postgres", "volume_id"), "wrong", "POSTGRES_TARGET_CHANGED"),
            (("postgres", "migrations_to_apply"), [], "MIGRATION_SET_NOT_REVIEWED"),
            (("postgres", "migration_compatibility_reviewed"), False, "MIGRATION_COMPATIBILITY_NOT_REVIEWED"),
            (("postgres", "flyway_history_read_only_confirmed"), False, "LIVE_FLYWAY_HISTORY_NOT_READ"),
            (("safeguards", "global_pilot_effects_disabled"), False, "PILOT_GLOBAL_KILL_SWITCH_NOT_VERIFIED"),
            (("safeguards", "other_tenants_unchanged"), False, "OTHER_TENANTS_NOT_PROTECTED"),
            (("safeguards", "health_rollback_operator_assigned"), False, "ROLLBACK_OPERATOR_NOT_ASSIGNED"),
        ]
        for path, bad_value, code in cases:
            with self.subTest(path=path):
                payload = copy.deepcopy(nominal())
                payload[path[0]][path[1]] = bad_value
                self.assertIn(code, check(payload).blockers)

    def test_backup_and_restore_require_independent_evidence(self):
        for path, wrong_value, blocked in [
            ("backup_id", "", "FRESH_BACKUP_NOT_VERIFIED"),
            ("snapshot_freshness_verified", False, "FRESH_BACKUP_NOT_VERIFIED"),
            ("restore_evidence_id", "", "ISOLATED_RESTORE_NOT_CERTIFIED"),
            ("isolated_restore_completed", False, "ISOLATED_RESTORE_NOT_CERTIFIED"),
            ("source_volume_untouched", False, "ISOLATED_RESTORE_NOT_CERTIFIED"),
        ]:
            with self.subTest(path=path):
                payload = nominal()
                payload["postgres"]["backup"][path] = wrong_value
                self.assertIn(blocked, check(payload).blockers)

    def test_no_canary_auth_or_live_call_go_can_be_inferred_from_release_gate(self):
        payload = nominal()
        payload["live_call_certified"] = True
        payload["commercial_go"] = True
        result = check(payload)
        self.assertFalse(result.as_dict()["live_call_certified"])
        self.assertFalse(result.as_dict()["commercial_go"])


if __name__ == "__main__":
    unittest.main()
