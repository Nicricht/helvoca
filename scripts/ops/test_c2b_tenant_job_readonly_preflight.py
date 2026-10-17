"""Offline, assertion-rich contracts for the non-mutating C2-B tenant/job probe."""
import base64
import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

import c2b_tenant_job_readonly_preflight as c


def good(**updates):
    return dict(probe="c2b-tenant-job-counts-v1", read_only=True,
                pg_major=18, privileged_scope=True, tenants_total=2,
                tenants_unmanaged=1, pilots_total=1, pilots_running=0,
                pilots_paused=1, jobs_pending=1, jobs_failed=0,
                jobs_running=0, jobs_running_expired_lease=0, **updates)


def wire(data=None):
    return (c.PREFIX + json.dumps(good() if data is None else data) + "\n").encode()


class TenantJobPreflightTest(unittest.TestCase):
    def test_sql_counters_only_with_bounded_read_only_transaction(self):
        sql = c.SQL
        self.assertTrue(sql.startswith("BEGIN TRANSACTION READ ONLY;"))
        self.assertTrue(sql.endswith("COMMIT;"))
        self.assertIn("statement_timeout = '10s'", sql)
        self.assertIn("current_setting('transaction_read_only')", sql)
        self.assertIn("rolsuper OR rolbypassrls", sql)
        self.assertIn("NOT EXISTS", sql)
        self.assertIn("lease_expires_at <= now()", sql)
        for token in ("INSERT INTO", "UPDATE ", "DELETE FROM", "TRUNCATE ",
                      "CREATE TABLE", "DROP TABLE", "ALTER TABLE", "COPY ",
                      "pg_sleep", "phone_number", "payload", "email"):
            self.assertNotIn(token.lower(), sql.lower())

    def test_ssh_project_environment_postgres_fixed_and_sql_base64(self):
        command = c.command("railway")
        self.assertEqual(command[:9], ["railway", "ssh", "--project", c.PROJECT,
                                       "--environment", c.ENVIRONMENT,
                                       "--service", c.POSTGRES_SERVICE, "--"])
        remote = command[-1]
        for value in (c.PROJECT, c.ENVIRONMENT, c.POSTGRES_SERVICE,
                      "RAILWAY_PROJECT_ID", "RAILWAY_ENVIRONMENT_ID", "RAILWAY_SERVICE_ID",
                      'PGPASSWORD="$POSTGRES_PASSWORD"', "psql -X -q -A -t"):
            self.assertIn(value, remote)
        payload = remote.split("printf %s ", 1)[1].split(" | base64 -d", 1)[0]
        self.assertEqual(base64.b64decode(payload).decode(), c.SQL)
        self.assertNotIn("pg_restore", remote)
        self.assertNotIn("pg_dump", remote)
        self.assertNotIn("railway run", remote)

    def test_parse_exact_safe_aggregate(self):
        result = c.parse(wire())
        self.assertEqual(result, good())
        self.assertEqual(set(c.COUNTS), set(result) - {
            "probe", "read_only", "pg_major", "privileged_scope"})

    def test_readonly_role_and_version_fail_closed(self):
        for changes in ({"read_only": False}, {"privileged_scope": False},
                        {"pg_major": 16}, {"pg_major": True}):
            with self.subTest(changes=changes):
                bad = good()
                bad.update(changes)
                with self.assertRaisesRegex(c.Blocked, "C2B_UNVERIFIED_READ_ONLY_DB_SCOPE"):
                    c.parse(wire(bad))

    def test_reject_payload_tampering_and_unexpected_keys(self):
        for case in (b"", b"broken", wire() + wire(), b"\xff", b"X" * 4097,
                     wire(dict(good(), customer="PRIVATE")),
                     wire({k: v for k, v in good().items() if k != "jobs_running"}),
                     wire(dict(good(), probe="forged"))):
            with self.subTest(case=case[:20]), self.assertRaises(c.Blocked):
                c.parse(case)

    def test_counts_are_nonnegative_plain_integers(self):
        for invalid in (-1, True, "1", 1.3, None):
            for key in c.COUNTS:
                data = good()
                data[key] = invalid
                with self.subTest(key=key, invalid=invalid), self.assertRaises(c.Blocked):
                    c.parse(wire(data))

    def test_counts_inconsistency_fails_closed(self):
        for change in ({"tenants_unmanaged": 3}, {"pilots_total": 3},
                       {"pilots_running": 1}, {"jobs_running_expired_lease": 1}):
            data = good()
            data.update(change)
            with self.subTest(change=change), self.assertRaisesRegex(c.Blocked, "C2B_INCONSISTENT_COUNTERS"):
                c.parse(wire(data))

    def test_probe_no_stderr_and_no_retries(self):
        with patch.object(c.subprocess, "run", return_value=Mock(returncode=0, stdout=wire())) as run:
            self.assertEqual(c.probe("railway"), good())
        self.assertEqual(run.call_count, 1)
        self.assertIs(run.call_args.kwargs["stderr"], subprocess.DEVNULL)
        self.assertEqual(run.call_args.kwargs["timeout"], 120)

    def test_probe_failure_redacts_and_fails_closed(self):
        for error in (OSError("PASSWORD_PRIVATE"), subprocess.TimeoutExpired("abc", 120)):
            with patch.object(c.subprocess, "run", side_effect=error), self.assertRaisesRegex(c.Blocked, "C2B_RAILWAY_READ_ONLY_UNAVAILABLE"):
                c.probe("railway")
        with patch.object(c.subprocess, "run", return_value=Mock(returncode=43, stdout=b"PASSWORD_PRIVATE")):
            with self.assertRaisesRegex(c.Blocked, "C2B_RAILWAY_IDENTITY_OR_PSQL_FAILED"):
                c.probe("railway")

    def test_private_evidence_only_in_safe_windows_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"LOCALAPPDATA": temp}):
                path = c.private_evidence(good())
            self.assertEqual(path.parent, Path(temp) / "Helvoca" / "private-evidence")
            data = json.loads(path.read_text())
            self.assertFalse(data["release_approval"])
            self.assertEqual("UNVERIFIED", data["external_effects_stopped"])
            self.assertEqual(data["counts"], good())

    def test_private_directory_refuses_symlink_and_git(self):
        with tempfile.TemporaryDirectory() as temp:
            git = Path(temp) / ".git"
            git.mkdir()
            with patch.dict(os.environ, {"LOCALAPPDATA": temp}), self.assertRaisesRegex(c.Blocked, "C2B_UNSAFE_PRIVATE_STORAGE"):
                c.private_evidence(good())
        with tempfile.TemporaryDirectory() as temp:
            outside = Path(temp) / "outside"
            outside.mkdir()
            target = Path(temp) / "Helvoca"
            target.symlink_to(outside, target_is_directory=True)
            with patch.dict(os.environ, {"LOCALAPPDATA": temp}), self.assertRaisesRegex(c.Blocked, "C2B_UNSAFE_PRIVATE_STORAGE"):
                c.private_evidence(good())

    def test_private_evidence_unavailable_or_not_writable(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(c.Blocked, "C2B_PRIVATE_STORAGE_UNAVAILABLE"):
            c.private_evidence(good())
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"LOCALAPPDATA": temp}), patch.object(Path, "open", side_effect=OSError("PRIVATE")):
                with self.assertRaisesRegex(c.Blocked, "C2B_PRIVATE_EVIDENCE_WRITE_FAILED"):
                    c.private_evidence(good())

    def test_main_optin_and_cli_missing(self):
        with contextlib.redirect_stdout(io.StringIO()) as stream:
            self.assertEqual(c.main([]), 2)
        self.assertIn("EXPLICIT_READ_ONLY", stream.getvalue())
        with patch.object(c.shutil, "which", return_value=None), contextlib.redirect_stdout(io.StringIO()) as stream:
            self.assertEqual(c.main(["--probe-read-only"]), 2)
        self.assertIn("C2B_RAILWAY_CLI_NOT_FOUND", stream.getvalue())

    def test_main_reads_once_private_and_refuses_stop_certification(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"LOCALAPPDATA": temp}), patch.object(c.shutil, "which", return_value="railway"), patch.object(c, "probe", return_value=good()) as query, contextlib.redirect_stdout(io.StringIO()) as stream:
                self.assertEqual(c.main(["--probe-read-only"]), 0)
                query.assert_called_once_with("railway")
        self.assertIn("C2B_COUNT_JOBS_RUNNING:0", stream.getvalue())
        self.assertIn("C2B_NO_RELEASE_APPROVAL", stream.getvalue())
        self.assertNotIn("COMMERCIAL_GO", stream.getvalue())

    def test_main_failure_never_reveals_inner_error(self):
        with patch.object(c.shutil, "which", return_value="railway"), patch.object(c, "probe", side_effect=c.Blocked("SAFE_REDACTED")), contextlib.redirect_stdout(io.StringIO()) as stream:
            self.assertEqual(c.main(["--probe-read-only"]), 2)
        self.assertIn("SAFE_REDACTED", stream.getvalue())
        self.assertNotIn("PASSWORD_PRIVATE", stream.getvalue())


if __name__ == "__main__":
    unittest.main()


class EntrypointTest(unittest.TestCase):
    def test_script_as_main_refuses_unspecified_probe(self):
        import runpy
        import sys
        with patch.object(sys, "argv", ["c2b_tenant_job_readonly_preflight.py"]), \
                contextlib.redirect_stdout(io.StringIO()) as stream, \
                self.assertRaises(SystemExit) as ended:
            runpy.run_path(str(Path(__file__).with_name("c2b_tenant_job_readonly_preflight.py")), run_name="__main__")
        self.assertEqual(2, ended.exception.code)
        self.assertIn("EXPLICIT_READ_ONLY", stream.getvalue())
