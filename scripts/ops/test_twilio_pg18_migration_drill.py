"""Offline safety contracts for Stage B private, network-disabled migration drill."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("stage_b", HERE / "twilio_pg18_migration_drill.py")
stage_b = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(stage_b)


class StageBDrillOfflineTests(unittest.TestCase):
    def test_exact_previously_attested_archive_used(self):
        self.assertEqual("helvoca-postgres18-20261013T041250Z-355b64.dump", stage_b.ARCHIVE)
        self.assertEqual(64, len(stage_b.EXPECTED_SHA256))
        self.assertEqual("06ed0b2dc9a41baf2b8a02f81725dd80ce6467a9", stage_b.SQL_REF)

    def test_migrations_are_ordered_and_exact(self):
        self.assertEqual([100, 101, 102, 103], list(stage_b.VERSIONS))
        self.assertEqual(3, len(stage_b.GUARDED_TABLES))
        self.assertTrue(all(".sql" in v for v in stage_b.VERSIONS.values()))

    def test_existing_phone_row_digest_ignores_only_new_v100_columns(self):
        expression = stage_b.PHONE_INVARIANT_SQL
        self.assertIn("to_jsonb(t)", expression)
        for name in ("twilio_binding_source", "twilio_account_sid", "voice_verified_at"):
            self.assertIn(name, expression)
        self.assertIn("ORDER BY t.id", expression)
        self.assertNotIn("t::text", expression)

    def test_digest_detects_file_mutation(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "private.dump"
            archive.write_bytes(b"PGDMP" + b"secret\x00")
            original = stage_b.sha256_file(archive)
            archive.write_bytes(b"PGDMP" + b"changed\x00")
            self.assertNotEqual(original, stage_b.sha256_file(archive))

    def test_fail_closed_invalid_condition(self):
        with self.assertRaisesRegex(RuntimeError, "denied"):
            stage_b.require(False, "denied")

    def test_sql_reads_are_docker_only_and_cannot_use_production(self):
        calls = []

        def safe_run(cmd, **kwargs):
            calls.append(cmd)
            return subprocess.CompletedProcess(cmd, 0, b"1\n")

        with patch.object(stage_b, "run", side_effect=safe_run):
            self.assertEqual(1, stage_b.number("docker", "scratch", "SELECT 1"))
            self.assertEqual("1", stage_b.psql("docker", "scratch", "SELECT 1",
                                               role="helvoca_runtime"))
        self.assertEqual(2, len(calls))
        self.assertTrue(all("-q" in c for c in calls), "psql must suppress SET tags")
        self.assertTrue(all(c[:3] == ["docker", "exec", "scratch"] for c in calls))
        self.assertFalse(any("railway" in " ".join(c) for c in calls))

    def test_reject_role_injection(self):
        with self.assertRaisesRegex(RuntimeError, "Forbidden role"):
            stage_b.psql("docker", "scratch", "SELECT 1", role="postgres;DROP")

    def test_pinned_migration_retrieval_is_read_only(self):
        class FakeResponse:
            def __init__(self, data):
                self.data = data
            def read(self, n):
                return self.data
            def __enter__(self):
                return self
            def __exit__(self, *a):
                return None
        urls = []
        def fake_urlopen(url, timeout):
            urls.append(url)
            return FakeResponse(b"-- staging SQL\n" + b"SELECT 1;\n" * 20)
        with patch.object(stage_b, "urlopen", side_effect=fake_urlopen):
            versions = stage_b.pinned_sql()
        self.assertEqual([100, 101, 102, 103], [v for v, _ in versions])
        self.assertEqual(4, len(urls))
        self.assertTrue(all(stage_b.SQL_REF in u for u in urls))
        self.assertTrue(all("raw.githubusercontent.com/Nicricht/helvoca/" in u for u in urls))


if __name__ == "__main__":
    unittest.main()
