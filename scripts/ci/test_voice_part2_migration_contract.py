"""Helvoca Part 2 frozen Flyway migration and no-production-regression tests."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "voice_part2_contract", ROOT / "scripts/ci/voice_part2_migration_contract.py"
)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class Part2MigrationSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        dst = self.root / "src/main/resources/db/migration"
        dst.mkdir(parents=True)
        for name, _ in audit.FROZEN_MIGRATIONS.values():
            shutil.copyfile(ROOT / "src/main/resources/db/migration" / name, dst / name)

    def script(self, v):
        return (self.root / "src/main/resources/db/migration"
                / audit.FROZEN_MIGRATIONS[v][0])

    def test_original_exact_git_blobs_and_contract_pass(self):
        result = audit.assess(self.root)
        self.assertEqual("PASS", result["offline_source_contract"], result["issues"])
        self.assertEqual([], result["issues"])

    def test_current_repository_same_as_copy(self):
        self.assertEqual("PASS", audit.assess(ROOT)["offline_source_contract"])

    def test_only_four_expected_versions(self):
        self.assertEqual([100, 101, 102, 103], list(audit.FROZEN_MIGRATIONS))
        for version in audit.FROZEN_MIGRATIONS:
            self.assertEqual(40, len(audit.FROZEN_MIGRATIONS[version][1]))

    def test_changed_flyway_migration_fails_checksum(self):
        self.script(101).write_bytes(self.script(101).read_bytes() + b"\n-- silent change\n")
        self.assertIn("MIGRATION_V101_CHANGED", audit.assess(self.root)["issues"])

    def test_all_versions_drift_blocked(self):
        for version in audit.FROZEN_MIGRATIONS:
            with self.subTest(version=version):
                path = self.script(version)
                previous = path.read_bytes()
                path.write_bytes(previous + b"\n-- tamper\n")
                self.assertIn(f"MIGRATION_V{version}_CHANGED",
                              audit.assess(self.root)["issues"])
                path.write_bytes(previous)

    def test_missing_sql_blocks(self):
        self.script(100).unlink()
        self.assertIn("MIGRATION_V100_FILENAME_OR_COUNT",
                      audit.assess(self.root)["issues"])

    def test_renamed_sql_blocks(self):
        self.script(103).rename(self.script(103).with_name("V103__renamed.sql"))
        self.assertIn("MIGRATION_V103_FILENAME_OR_COUNT",
                      audit.assess(self.root)["issues"])

    def test_duplicate_version_blocks(self):
        source = self.script(102)
        shutil.copyfile(source, source.with_name("V102__duplicate.sql"))
        self.assertIn("MIGRATION_V102_FILENAME_OR_COUNT",
                      audit.assess(self.root)["issues"])

    def test_legacy_phone_dml_blocks(self):
        for sql in ("UPDATE public.phone_number SET external_id = 'bad';",
                    "INSERT INTO phone_number(id) VALUES (NULL);",
                    "DELETE FROM public.phone_number WHERE TRUE;"):
            with self.subTest(sql=sql):
                path = self.script(100)
                previous = path.read_text()
                path.write_text(previous + "\n" + sql + "\n")
                result = audit.assess(self.root)
                self.assertIn("MIGRATION_V100_LEGACY_PHONE_DML", result["issues"])
                path.write_text(previous)

    def test_missing_verified_existing_guard_blocks(self):
        path = self.script(101)
        path.write_text(path.read_text().replace(
            "helvoca_verified_existing_write_guard", "removed_guard"))
        result = audit.assess(self.root)
        self.assertIn("MIGRATION_V101_CONTRACT_DRIFT", result["issues"])

    def test_missing_force_rls_blocks(self):
        path = self.script(102)
        path.write_text(path.read_text().replace(
            "FORCE ROW LEVEL SECURITY", "DISABLE ROW LEVEL SECURITY"))
        result = audit.assess(self.root)
        self.assertIn("MIGRATION_V102_CONTRACT_DRIFT", result["issues"])

    def test_no_unapproved_release_claims(self):
        path = self.script(103)
        path.write_text(path.read_text() + "\n-- PROD_DEPLOY=GO\n")
        self.assertIn("MIGRATION_V103_UNAUTHORIZED_GO",
                      audit.assess(self.root)["issues"])

    def test_no_approval_even_when_source_valid(self):
        result = audit.assess(self.root)
        self.assertEqual("NO-GO", result["prod_deploy"])
        self.assertFalse(result["production_sql_applied"])
        self.assertFalse(result["may_merge"])
        self.assertFalse(result["commercial_go"])
        self.assertFalse(result["live_call_certified"])
        self.assertEqual("NOT_RUN", result["flyway_real_restored_dump_upgrade"])

    def test_missing_directory_blocks(self):
        shutil.rmtree(self.root / "src/main/resources/db/migration")
        result = audit.assess(self.root)
        self.assertEqual("BLOCKED", result["offline_source_contract"])
        self.assertEqual(4, len(result["issues"]))

    def test_contract_does_not_read_prod_secrets_or_make_provider_calls(self):
        script = (ROOT / "scripts/ci/voice_part2_migration_contract.py").read_text()
        for unexpected in ("import requests", "import subprocess",
                           "urllib.request", "TWILIO_AUTH_TOKEN", "PGPASSWORD",
                           "railway ssh", "railway run"):
            self.assertNotIn(unexpected, script)


if __name__ == "__main__":
    unittest.main()
