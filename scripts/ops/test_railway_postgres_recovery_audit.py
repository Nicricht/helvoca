"""Non-network regression tests for read-only Railway recovery inventory."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("recovery", ROOT / "railway_postgres_recovery_audit.py")
recovery = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(recovery)


class RecoveryInventoryTests(unittest.TestCase):
    def test_allowed_commands_are_read_only(self):
        self.assertEqual((("postgres", "pitr", "status"),
                          ("postgres", "pitr", "backup", "list")),
                         recovery.READ_ONLY_COMMANDS)

    def test_inventory_never_certifies_restore_or_production(self):
        commands = []
        def fake_runner(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, b'{"status":"ok"}', b"")
        with tempfile.TemporaryDirectory() as tmp:
            report = recovery.audit("railway", Path(tmp) / "private", runner=fake_runner)
            self.assertEqual(2, len(commands))
            self.assertTrue(all("--project" in c and "--environment" in c
                                and "--service" in c and "--json" in c for c in commands))
            self.assertEqual("RESTORE_PROOF_MISSING", report["conclusion"])
            self.assertFalse(report["restoration_test_completed"])
            self.assertFalse(report["production_deployment_authorized"])
            self.assertFalse(report["live_call_certified"])
            self.assertFalse(report["commercial_go"])
            self.assertTrue((Path(tmp) / "private" / "recovery-report.private.json").is_file())

    def test_failed_cli_does_not_claim_success(self):
        def failed_runner(command, **kwargs):
            return subprocess.CompletedProcess(command, 3, b"", b"private failure")
        with tempfile.TemporaryDirectory() as tmp:
            report = recovery.audit("railway", Path(tmp) / "private", runner=failed_runner)
            self.assertFalse(any(q["completed_without_cli_error"] for q in report["queries"]))
            self.assertEqual("RESTORE_PROOF_MISSING", report["conclusion"])

    def test_git_worktree_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".git").mkdir()
            with self.assertRaises(ValueError):
                recovery.safe_write(root / "private", "report.json", b"{}")


    def test_fresh_completed_native_backup_is_only_metadata_evidence(self):
        from datetime import datetime, timezone
        checked = datetime(2026, 10, 15, 10, 0, tzinfo=timezone.utc)
        payload = b'{"backups":[{"id":"never-echo","status":"COMPLETED","completedAt":"2026-10-15T08:00:00Z"}]}'
        finding = recovery.backup_freshness(payload, checked)
        self.assertEqual("RECENT_COMPLETED_METADATA", finding["state"])
        self.assertEqual(7200, finding["age_seconds"])
        self.assertTrue(finding["completed_within_24h_by_metadata"])
        self.assertNotIn("never-echo", str(finding))

    def test_old_completed_native_backup_is_not_fresh(self):
        from datetime import datetime, timezone
        checked = datetime(2026, 10, 15, 10, 0, tzinfo=timezone.utc)
        payload = b'{"items":[{"status":"success","finished_at":"2026-10-13T08:00:00+00:00"}]}'
        finding = recovery.backup_freshness(payload, checked)
        self.assertEqual("STALE_COMPLETED_METADATA", finding["state"])
        self.assertFalse(finding["completed_within_24h_by_metadata"])

    def test_missing_timestamp_or_completion_never_proves_freshness(self):
        from datetime import datetime, timezone
        checked = datetime(2026, 10, 15, 10, 0, tzinfo=timezone.utc)
        for payload in (
            b'[{"status":"PENDING","completedAt":"2026-10-15T08:00:00Z"}]',
            b'[{"status":"COMPLETED","createdAt":"2026-10-15T08:00:00Z"}]',
            b'[{"status":"COMPLETED","completedAt":"2026-10-15T08:00:00"}]',
            b'[{"status":"COMPLETED","completedAt":"2026-10-16T08:00:00Z"}]',
            b'[{"status":"COMPLETED","completedAt":"not-a-date"}]',
            b'[1, null, {"status":"FAILED","completedAt":"2026-10-15T08:00:00Z"}]',
        ):
            with self.subTest(payload=payload):
                finding = recovery.backup_freshness(payload, checked)
                self.assertEqual("COMPLETION_UNVERIFIED", finding["state"])
                self.assertFalse(finding["completed_within_24h_by_metadata"])

    def test_invalid_unknown_and_empty_backup_lists_are_fail_closed(self):
        from datetime import datetime, timezone
        checked = datetime(2026, 10, 15, 10, 0, tzinfo=timezone.utc)
        for raw in (b"not-json", b'{"unexpected":[]}', b"null"):
            self.assertEqual("UNVERIFIED", recovery.backup_freshness(raw, checked)["state"])
        self.assertEqual("NO_BACKUPS_LISTED",
                         recovery.backup_freshness(b'{"backups":[]}', checked)["state"])
        naive = datetime(2026, 10, 15, 10, 0)
        self.assertEqual("UNVERIFIED",
                         recovery.backup_freshness(b"[]", naive)["state"])

    def test_nested_mixed_backup_list_uses_latest_verified_completion(self):
        from datetime import datetime, timezone
        checked = datetime(2026, 10, 15, 10, 0, tzinfo=timezone.utc)
        raw = (b'{"data":{"volumeBackups":['
               b'{"state":"SUCCEEDED","finishedAtUtc":"2026-10-14T08:00:00Z"},'
               b'{"state":"SUCCEEDED","finishedAtUtc":"2026-10-15T09:00:00Z"},'
               b'{"state":"FAILED","finishedAtUtc":"2026-10-15T09:30:00Z"}]}}')
        result = recovery.backup_freshness(raw, checked)
        self.assertEqual("RECENT_COMPLETED_METADATA", result["state"])
        self.assertEqual(3600, result["age_seconds"])

    def test_inventory_audit_adds_safe_freshness_without_claiming_restore(self):
        from datetime import datetime, timezone
        completed = datetime.now(timezone.utc).isoformat()
        commands = []
        def fake_runner(command, **kwargs):
            commands.append(command)
            body = (('{"backups":[{"id":"sensitive-backup-ref","status":"COMPLETED",'
                     '"completedAt":"' + completed + '"}]}').encode()
                    if "backup" in command else b'{"status":"off"}')
            return subprocess.CompletedProcess(command, 0, body, b"")
        with tempfile.TemporaryDirectory() as tmp:
            report = recovery.audit("railway", Path(tmp) / "private", runner=fake_runner)
        self.assertEqual(2, len(commands))
        self.assertEqual("RECENT_COMPLETED_METADATA",
                         report["volume_backup_freshness"]["state"])
        self.assertFalse(report["restoration_test_completed"])
        self.assertFalse(report["commercial_go"])
        self.assertNotIn("sensitive-backup-ref",
                         str(report["volume_backup_freshness"]))



if __name__ == "__main__":
    unittest.main()
