"""Offline, deterministic safety tests for the Railway PostgreSQL 18 restore drill."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("snapshot", HERE / "railway_pg18_snapshot_drill.py")
snapshot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot)


class BackupDrillTests(unittest.TestCase):
    def test_sensitive_ssh_errors_only_produce_safe_category(self):
        self.assertEqual("SSH_KEY_OR_AUTH",
                         snapshot.classify_ssh_error(b"Permission denied (publickey)."))
        self.assertEqual("PG_DUMP_OR_DATABASE_AUTH",
                         snapshot.classify_ssh_error(b"pg_dump: error: sensitive details"))
        self.assertEqual("UNCLASSIFIED",
                         snapshot.classify_ssh_error(b"unknown private text"))

    def test_archive_signature_and_digest(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "valid.dump"
            path.write_bytes(b"PGDMP" + b"x" * 1200)
            info = snapshot.validate_dump(path)
            self.assertEqual(1205, info["bytes"])
            self.assertEqual(64, len(info["sha256"]))

    def test_invalid_archive_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "invalid.dump"
            path.write_bytes(b"passwords" + b"x" * 1200)
            with self.assertRaises(ValueError):
                snapshot.validate_dump(path)

    def test_small_archive_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "small.dump"
            path.write_bytes(b"PGDMP")
            with self.assertRaises(ValueError):
                snapshot.validate_dump(path)

    def test_ssh_dump_is_read_only(self):
        remote = snapshot.SSH_DUMP
        self.assertIn("pg_dump", remote)
        self.assertNotIn("pg_restore", remote)
        self.assertNotIn("createdb", remote)
        self.assertIn('"$POSTGRES_PASSWORD"', remote)

    def test_restore_uses_disposable_offline_container_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as d:
            dump = Path(d) / "x.dump"
            dump.write_bytes(b"PGDMP" + b"x" * 2048)
            calls = []
            def fake_run(command, **kwargs):
                calls.append(command)
                if "psql" in command:
                    sql = command[-1]
                    output = (b"7\n" if "pg_tables" in sql else
                              b"5\n" if "to_regclass" in sql else
                              b"2\n" if "flyway_schema_history" in sql else b"1\n")
                else:
                    output = b"ok"
                return subprocess.CompletedProcess(command, 0, output)
            with patch.object(snapshot, "run_quiet", side_effect=fake_run):
                out = snapshot.restore_to_disposable_docker("docker", dump)
            self.assertEqual(5, out["core_tables"])
            self.assertEqual(2, out["flyway_history_rows"])
            create = next(c for c in calls if len(c)>1 and c[1] == "run")
            self.assertIn("--network", create)
            self.assertIn("none", create)
            self.assertNotIn("-p", create)
            self.assertIn("postgres:18", create)
            self.assertEqual("rm", calls[-1][1])

    def test_bad_core_tables_fail_closed_and_cleanup(self):
        with tempfile.TemporaryDirectory() as d:
            dump = Path(d) / "x.dump"
            dump.write_bytes(b"PGDMP" + b"x" * 2048)
            calls = []
            def fake_run(command, **kwargs):
                calls.append(command)
                return subprocess.CompletedProcess(command, 0, b"0\n" if "psql" in command else b"ok")
            with patch.object(snapshot, "run_quiet", side_effect=fake_run):
                with self.assertRaises(RuntimeError):
                    snapshot.restore_to_disposable_docker("docker", dump)
            self.assertEqual("rm", calls[-1][1])


if __name__ == "__main__":
    unittest.main()
