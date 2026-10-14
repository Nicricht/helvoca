"""Adversarial tests for Part 6 isolated backup/recovery workflow definition."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "part6_contract", ROOT / "scripts/ci/voice_part6_recovery_contract.py"
)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class Part6RecoverySafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = audit.WORKFLOW.read_text(encoding="utf-8")
        cls.drill = audit.DRILL.read_text(encoding="utf-8")

    def check_blocked(self, workflow=None, drill=None, expected=None):
        result = audit.assess(
            self.workflow if workflow is None else workflow,
            self.drill if drill is None else drill,
        )
        self.assertEqual("BLOCKED", result["definition"], result)
        if expected is not None:
            self.assertIn(expected, result["blockers"])
        self.assertEqual("NO-GO", result["prod_deploy"])
        self.assertFalse(result["may_merge"])

    def test_original_is_offline_and_still_no_go(self):
        result = audit.assess(self.workflow, self.drill)
        self.assertEqual("PASS_OFFLINE_DEFINITION", result["definition"], result)
        self.assertEqual([], result["blockers"])
        self.assertEqual("UNVERIFIED", result["fresh_production_backup"])
        self.assertEqual("UNVERIFIED", result["encrypted_offsite_backup"])
        self.assertEqual("NOT_VERIFIED_BY_STATIC_AUDITOR", result["synthetic_restore_run"])
        self.assertFalse(result["live_call_certified"])
        self.assertFalse(result["commercial_go"])
        self.assertFalse(result["rollback_approved"])

    def test_missing_inputs_fail_closed(self):
        self.check_blocked(workflow="", expected="SOURCE_MISSING")
        self.check_blocked(drill="", expected="SOURCE_MISSING")

    def test_missing_seventh_style_job_fails(self):
        self.check_blocked(
            workflow=self.workflow.replace(
                "  pg18-flyway-rls-regression:", "  renamed-rls-regression:"),
            expected="RECOVERY_JOBS_MISMATCH")

    def test_no_branch_main_trigger(self):
        self.check_blocked(
            workflow=self.workflow.replace(
                "jobs:\n", "    - 'main'\n\njobs:\n", 1),
            expected="MAIN_TRIGGER_FORBIDDEN")

    def test_no_overbroad_github_token(self):
        self.check_blocked(
            workflow=self.workflow.replace("contents: read", "contents: write", 1),
            expected="READ_ONLY_GITHUB_PERMISSIONS_REQUIRED")

    def test_no_live_provider_flags(self):
        for flag in audit.OFF_FLAGS:
            with self.subTest(flag=flag):
                mutated = self.workflow.replace(
                    flag + ": 'false'", flag + ": 'true'", 1)
                self.check_blocked(
                    workflow=mutated,
                    expected="EXTERNAL_EFFECT_FLAG_NOT_OFF_" + flag)

    def test_networked_restore_container_rejected(self):
        mutated = self.drill.replace("docker run --detach", "docker run -p 5432:5432 --detach", 1)
        self.check_blocked(drill=mutated, expected="TWO_NETWORK_DISABLED_CONTAINERS_REQUIRED")

    def test_missing_network_isolation_rejected(self):
        mutated = self.drill.replace("--network none", "--network bridge", 1)
        self.check_blocked(
            drill=mutated, expected="TWO_NETWORK_DISABLED_CONTAINERS_REQUIRED")

    def test_remote_pg_restore_rejected(self):
        self.check_blocked(
            drill=self.drill + "\npg_restore -h prod.example.test\n",
            expected="FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")

    def test_railway_command_rejected(self):
        self.check_blocked(
            drill=self.drill + "\nrailway deploy\n",
            expected="FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")

    def test_secret_reference_rejected(self):
        self.check_blocked(
            workflow=self.workflow + "\n# secrets.DATABASE_URL\n",
            expected="FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")

    def test_missing_checksum_or_encryption_rejected(self):
        self.check_blocked(
            drill=self.drill.replace("sha256sum", "sha2sum"),
            expected="SYNTHETIC_ENCRYPTION_INTEGRITY_MISSING")
        self.check_blocked(
            drill=self.drill.replace("openssl enc -aes-256-cbc -pbkdf2", "openssl enc -aes-256-cbc", 1),
            expected="SYNTHETIC_ENCRYPTION_INTEGRITY_MISSING")

    def test_missing_flyway_regression_fails(self):
        self.check_blocked(
            workflow=self.workflow.replace("PostgresRowLevelSecurityIntegrationTest", "RemovedRlsTest"),
            expected="REAL_FLYWAY_RLS_TESTS_MISSING")

    def test_missing_source_integrity_fails(self):
        self.check_blocked(
            workflow=self.workflow.replace(
                "python3 scripts/ci/voice_part2_migration_contract.py",
                "echo bypass"),
            expected="FROZEN_MIGRATION_CHECK_MISSING")

    def test_missing_explicit_unverified_prod_fails(self):
        self.check_blocked(
            drill=self.drill.replace(
                "PART6_PRODUCTION_BACKUP=UNVERIFIED",
                "PART6_PRODUCTION_BACKUP=VERIFIED"),
            expected="PRODUCTION_BACKUP_MUST_NOT_BE_CERTIFIED")

    def test_no_public_smoke_or_artifact_export(self):
        self.check_blocked(
            workflow=self.workflow + "\n# upload-artifact\n",
            expected="FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")
        self.check_blocked(
            workflow=self.workflow + "\n# recepvoz.cl\n",
            expected="FORBIDDEN_NETWORK_OR_MUTATION_OR_SECRET_REFERENCE")


if __name__ == "__main__":
    unittest.main()
