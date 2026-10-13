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


if __name__ == "__main__":
    unittest.main()
