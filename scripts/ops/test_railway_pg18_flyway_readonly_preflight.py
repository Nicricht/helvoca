"""Offline safety tests for Stage D Railway PostgreSQL Flyway read-only probe."""
import importlib.util
import base64
import re
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("railway_pg18_flyway_readonly_preflight.py")
SPEC = importlib.util.spec_from_file_location("stage_d_readonly", SCRIPT)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


def safe_catalog(**changes):
    base = {
        "probe": "stage-d-flyway-catalog-v1",
        "read_only": True,
        "pg_major": 18,
        "history_count": 98,
        "v99_success_count": 1,
        "later_voice_migrations": 0,
        "history_failed": 0,
        "last_version": "99",
        "provenance_columns": 0,
        "future_tables": 0,
    }
    return {**base, **changes}


def response(**changes):
    return ("\n" + probe.RESULT_PREFIX + json.dumps(safe_catalog(**changes))
            + "\n").encode("utf-8")


class StageDReadonlyContracts(unittest.TestCase):
    def test_sql_executes_one_read_only_transaction(self):
        sql = probe.SQL
        self.assertTrue(sql.startswith("BEGIN TRANSACTION READ ONLY;"))
        self.assertTrue(sql.endswith("COMMIT;"))
        self.assertIn("current_setting('transaction_read_only')", sql)
        self.assertIn("flyway_schema_history", sql)
        for keyword in ("INSERT INTO", "UPDATE ", "DELETE FROM", "TRUNCATE ",
                        "CREATE TABLE", "DROP TABLE", "ALTER TABLE", "COPY ",
                        "PERFORM ", "pg_sleep", "phone_number t", "business b"):
            self.assertNotIn(keyword, sql.upper() if keyword == keyword.upper() else sql)

    def test_ssh_only_to_pinned_railway_pg_and_password_via_remote_env(self):
        cmd = probe.railway_command("railway")
        self.assertEqual(["railway", "ssh", "--project", probe.PROJECT], cmd[:4])
        self.assertEqual(probe.POSTGRES_SERVICE, cmd[cmd.index("--service") + 1])
        self.assertEqual("sh", cmd[-3])
        self.assertEqual("-c", cmd[-2])
        self.assertIn("PGPASSWORD", cmd[-1])
        self.assertIn("psql -X -q -A -t", cmd[-1])
        self.assertIn("base64 -d", cmd[-1])
        # SQL is transported as ASCII base64; no nested single-quote
        # fragments to confuse Railway SSH command re-tokenization.
        encoded = cmd[-1].split("printf %s ", 1)[1].split(" | base64 -d", 1)[0]
        self.assertTrue(re.fullmatch(r"[A-Za-z0-9+/=]+", encoded))
        self.assertEqual(probe.SQL, base64.b64decode(encoded).decode())
        self.assertNotIn("STAGE_D_FLYWAY_PROBE:", cmd[-1])
        self.assertNotIn("pg_dump", cmd[-1])
        self.assertNotIn("pg_restore", cmd[-1])
        self.assertNotIn("--port", cmd)
        self.assertFalse(any(str(arg).startswith("AC") or str(arg).startswith("PN")
                             for arg in cmd))

    def test_parse_only_metadata_in_expected_json(self):
        data = probe.parse_result(response())
        self.assertEqual(safe_catalog(), data)
        self.assertTrue(probe.evaluate(data)[0])

    def test_extra_fields_are_discarded(self):
        data = safe_catalog(customer_name="DO_NOT_EXPORT", email="PRIVATE")
        found = probe.parse_result(
            (probe.RESULT_PREFIX + json.dumps(data) + "\n").encode())
        self.assertNotIn("customer_name", found)
        self.assertNotIn("email", found)

    def test_duplicate_or_missing_probe_results_fail(self):
        for data in (b"\n", response() + response(), b"{}\n"):
            with self.subTest(data=data[:15]):
                with self.assertRaises(RuntimeError):
                    probe.parse_result(data)

    def test_invalid_json_and_invalid_types_fail(self):
        with self.assertRaises(RuntimeError):
            probe.parse_result((probe.RESULT_PREFIX + "{invalid}\n").encode())
        for change in ({"read_only": "true"}, {"history_count": True},
                       {"history_failed": -1}, {"pg_major": "18"}):
            with self.subTest(change=change):
                with self.assertRaises(RuntimeError):
                    probe.parse_result(response(**change))

    def test_too_large_output_fails(self):
        with self.assertRaises(RuntimeError):
            probe.parse_result(b"X" * 40000)

    def test_read_write_mode_blocks(self):
        ok, reasons = probe.evaluate(safe_catalog(read_only=False))
        self.assertFalse(ok)
        self.assertIn("NOT_READ_ONLY", reasons)

    def test_postgres_version_drift_blocks(self):
        ok, reasons = probe.evaluate(safe_catalog(pg_major=16))
        self.assertFalse(ok)
        self.assertIn("POSTGRES_NOT_18", reasons)

    def test_flyway_missing_or_failed_blocks(self):
        for changes, code in (
            ({"history_count": 2}, "INCOMPLETE_FLYWAY_HISTORY"),
            ({"v99_success_count": 0}, "V99_NOT_APPLIED_ONCE"),
            ({"later_voice_migrations": 1}, "FUTURE_V100_V103_ALREADY_PRESENT"),
            ({"history_failed": 1}, "FAILED_FLYWAY_MIGRATION_EXISTS"),
            ({"last_version": "98"}, "LAST_FLYWAY_VERSION_NOT_99"),
        ):
            with self.subTest(changes=changes):
                ok, errors = probe.evaluate(safe_catalog(**changes))
                self.assertFalse(ok)
                self.assertIn(code, errors)

    def test_schema_drift_blocks(self):
        for changes, code in (
            ({"provenance_columns": 1}, "UNEXPECTED_V100_COLUMNS"),
            ({"future_tables": 1}, "UNEXPECTED_V102_V103_TABLES"),
        ):
            ok, reasons = probe.evaluate(safe_catalog(**changes))
            self.assertFalse(ok)
            self.assertIn(code, reasons)

    def test_private_location_never_git_or_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.dict(os.environ, {"LOCALAPPDATA": tmp}):
                path = probe.private_directory()
                self.assertEqual("private-backups", path.name)
                self.assertTrue(path.is_dir())
            git = Path(tmp) / "repo"
            git.mkdir()
            (git / ".git").mkdir()
            with patch.dict(os.environ, {"LOCALAPPDATA": str(git)}):
                with self.assertRaisesRegex(RuntimeError, "unsafe"):
                    probe.private_directory()

    def test_windows_backup_directory_permission_denial_uses_private_fallback(self):
        real_open = Path.open

        def deny_backups(path, *args, **kwargs):
            if (path.parent.name == "private-backups"
                    and path.name.startswith("helvoca-stage-d-")
                    and "x" in (args[0] if args else kwargs.get("mode", "r"))):
                raise PermissionError(13, "Permission denied")
            return real_open(path, *args, **kwargs)

        with tempfile.TemporaryDirectory() as tmp:
            with patch.dict(os.environ, {"LOCALAPPDATA": tmp}), \
                    patch.object(Path, "open", deny_backups):
                path = probe.write_private_evidence({"status": "BLOCKED"})
                self.assertEqual("private-evidence", path.parent.name)
                self.assertTrue(path.exists())
                self.assertEqual("BLOCKED", json.loads(path.read_text())["status"])
                self.assertEqual([], list((Path(tmp) / "Helvoca" / "private-backups")
                                            .glob("*.private.json")))

    def test_both_private_locations_denied_fails_closed_no_stdout_evidence(self):
        real_open = Path.open

        def refuse_reports(path, *args, **kwargs):
            if (path.name.startswith("helvoca-stage-d-")
                    and "x" in (args[0] if args else kwargs.get("mode", "r"))):
                raise PermissionError(13, "Folder protection active")
            return real_open(path, *args, **kwargs)

        with tempfile.TemporaryDirectory() as tmp:
            with patch.dict(os.environ, {"LOCALAPPDATA": tmp}), \
                    patch.object(Path, "open", refuse_reports):
                with self.assertRaisesRegex(RuntimeError, "Windows denied"):
                    probe.write_private_evidence({"secret": "never-to-stdout"})
                self.assertFalse(list(Path(tmp).rglob("*.private.json")))

    def test_evidence_folder_allowlist_blocks_arbitrary_location(self):
        with self.assertRaisesRegex(RuntimeError, "allowlist"):
            probe.private_directory("../../../git")

    def test_main_simulation_verifies_no_remote_mutations_and_only_safe_stdout(self):
        with tempfile.TemporaryDirectory() as tmp:
            fake = subprocess.CompletedProcess([], 0, response(), b"SECRET_NEVER_LOG")
            with patch.dict(os.environ, {"LOCALAPPDATA": tmp}), \
                    patch.object(probe.shutil, "which", return_value="railway"), \
                    patch.object(probe.subprocess, "run", return_value=fake) as run, \
                    patch("builtins.print") as printer:
                self.assertEqual(0, probe.main())
                self.assertEqual(1, run.call_count)
                kwargs = run.call_args.kwargs
                self.assertEqual(b"", kwargs["input"])
                self.assertIs(kwargs["stderr"], subprocess.PIPE)
                self.assertLessEqual(kwargs["timeout"], 120)
                combined = "\n".join(str(x) for c in printer.call_args_list for x in c.args)
                self.assertIn("STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED", combined)
                self.assertNotIn("SECRET_NEVER_LOG", combined)
                private_files = list((Path(tmp) / "Helvoca" / "private-backups")
                                     .glob("helvoca-stage-d-*.private.json"))
                self.assertEqual(1, len(private_files))
                evidence = json.loads(private_files[0].read_text())
                self.assertFalse(evidence["database_modified"])
                self.assertEqual("NO-GO", evidence["production_deploy"])
                self.assertFalse(evidence["commercial_go"])

    def test_remote_errors_are_categorized_without_raw_secrets(self):
        cases = {
            b"Error: permission denied (publickey); secret 123": "SSH_KEY_OR_AUTH",
            b"psql: FATAL: password authentication failed for user x": "POSTGRES_AUTH",
            b'ERROR: relation "public.flyway_schema_history" does not exist':
                "FLYWAY_TABLE_ACCESS_OR_ABSENT",
            b"sh: base64: not found": "REMOTE_BASE64_UNAVAILABLE",
            b"ERROR: syntax error at or near": "REMOTE_SQL_SYNTAX",
            b"sh: psql: not found": "PSQL_COMMAND_UNAVAILABLE",
            b"unexplained private details": "REMOTE_ERROR_UNCLASSIFIED",
        }
        for diagnostic, expected in cases.items():
            with self.subTest(expected=expected):
                self.assertEqual(expected, probe.classify_remote_error(diagnostic))
                self.assertNotIn("secret", probe.classify_remote_error(diagnostic))

    def test_remote_failure_stdout_does_not_leak_private_details(self):
        secret = b"password=highly-private"
        fake = subprocess.CompletedProcess([], 1, secret, secret)
        with patch.object(probe.shutil, "which", return_value="railway"), \
                patch.object(probe.subprocess, "run", return_value=fake), \
                patch("builtins.print") as printer:
            self.assertEqual(2, probe.main())
            printed = "\\n".join(str(arg) for call in printer.call_args_list
                                for arg in call.args)
            self.assertIn("category=REMOTE_ERROR_UNCLASSIFIED", printed)
            self.assertNotIn(secret.decode(), printed)

    def test_main_fails_closed_if_remote_nonzero(self):
        with patch.object(probe.shutil, "which", return_value="railway"), \
                patch.object(probe.subprocess, "run", return_value=
                    subprocess.CompletedProcess([], 1, b"", b"secret")):
            self.assertEqual(2, probe.main())


if __name__ == "__main__":
    unittest.main()
