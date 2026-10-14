#!/usr/bin/env python3
"""Offline tests: no Railway, remote upload, rclone or real encryption invoked."""
from pathlib import Path
import hashlib
import tempfile
import unittest
from unittest.mock import patch

import c1_unattended_institutional_backup as tool

RECIPIENT = "age1" + "q" * 58
DUMP = b"PGDMP" + b"SYNTHETIC_ONLY" * 300
CIPHER = tool.AGE_HEADER + DUMP + b"ENCRYPTED_SYNTHETIC"


class UnattendedTests(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.addCleanup(self.t.cleanup)
        self.root = Path(self.t.name) / "private"
        self.remote = "institutional:HelvocaBackups"
        self.events = []

    def export(self, cli, path):
        self.assertEqual(cli, "railway-test")
        self.assertTrue(path.is_relative_to(self.root / "private-backups"))
        path.write_bytes(DUMP)
        self.events.append("export")
        return {"bytes": len(DUMP), "sha256": hashlib.sha256(DUMP).hexdigest()}

    def command(self, args, *, failure, timeout=900):
        self.assertNotIn("SECRET", str(args))
        if args[:3] == ["rclone-test", "lsf", self.remote]:
            self.events.append("preflight")
        elif args[0] == "age-test":
            self.assertEqual(args[1:4], ["--encrypt", "-r", RECIPIENT])
            source = Path(args[-1])
            self.assertEqual(source.read_bytes(), DUMP)
            dst = Path(args[args.index("--output") + 1])
            self.assertTrue(dst.is_relative_to(self.root))
            dst.write_bytes(CIPHER)
            self.events.append("encrypt")
        elif args[0:3] == ["rclone-test", "copyto", "--immutable"]:
            local = Path(args[3])
            self.assertEqual(local.read_bytes(), CIPHER)
            self.assertTrue(args[4].startswith(self.remote + "/"))
            self.assertTrue(args[4].endswith(".dump.age"))
            self.events.append("upload")
        else:
            self.fail("Unrecognized command: " + str(args))

    def readback(self, cli, remote):
        self.assertEqual(cli, "rclone-test")
        self.assertTrue(remote.startswith(self.remote + "/"))
        self.events.append("readback")
        return (len(CIPHER), hashlib.sha256(CIPHER).hexdigest())

    def execute(self):
        with patch.object(tool, "dump_from_railway_ascii", side_effect=self.export), \
                patch.object(tool, "safe_run", side_effect=self.command), \
                patch.object(tool, "remote_cipher_digest", side_effect=self.readback):
            return tool.run_backup(private_root=self.root, remote=self.remote,
                                   recipient=RECIPIENT, railway="railway-test",
                                   age="age-test", rclone="rclone-test")

    def test_export_encrypt_upload_remote_readback_and_cleanup(self):
        result = self.execute()
        self.assertEqual(self.events, ["preflight", "export", "encrypt", "upload", "readback"])
        self.assertEqual(result["remote_sha256"], hashlib.sha256(CIPHER).hexdigest())
        self.assertEqual(result["remote_bytes"], len(CIPHER))
        self.assertFalse(result["commercial_go"])
        self.assertFalse(result["rpo_rto_sla_certified"])
        self.assertFalse(result["separated_private_key_custody_certified"])
        self.assertEqual(list((self.root / "private-backups").glob("**/*.dump")), [])
        self.assertEqual(list((self.root / "private-backups").glob("**/*.age")), [])

    def test_hash_readback_mismatch_never_reports_success(self):
        with patch.object(tool, "dump_from_railway_ascii", side_effect=self.export), \
                patch.object(tool, "safe_run", side_effect=self.command), \
                patch.object(tool, "remote_cipher_digest", return_value=(len(CIPHER), "a"*64)):
            with self.assertRaisesRegex(tool.SafeBlocker, "C1_REMOTE_CIPHER_SHA256_MISMATCH"):
                tool.run_backup(private_root=self.root, remote=self.remote,
                                recipient=RECIPIENT, railway="railway-test",
                                age="age-test", rclone="rclone-test")
        self.assertEqual(list((self.root / "private-backups").glob("**/*.dump")), [])
        self.assertEqual(list((self.root / "private-backups").glob("**/*.age")), [])

    def test_not_enabled_by_default(self):
        with self.assertRaisesRegex(tool.SafeBlocker, "C1_UNATTENDED_NOT_ENABLED"):
            tool.require_approval(execute=False, env={})

    def test_both_execution_gates_are_required(self):
        with self.assertRaises(tool.SafeBlocker):
            tool.require_approval(execute=True, env={})
        with self.assertRaises(tool.SafeBlocker):
            tool.require_approval(execute=False, env={tool.ENABLE_ENV:tool.ENABLE_VALUE})
        tool.require_approval(execute=True, env={tool.ENABLE_ENV:tool.ENABLE_VALUE})

    def test_cli_does_not_touch_any_provider_without_enable(self):
        with patch.object(tool, "run_backup") as backup, patch.object(tool.shutil, "which") as which:
            self.assertEqual(tool.main(["--remote", self.remote, "--recipient", RECIPIENT]), 2)
            backup.assert_not_called()
            which.assert_not_called()

    def test_invalid_remote_rejected_without_export(self):
        for remote in ("local:folder", "/absolute", "r:../../secret",
                       "r:folder;rm", "institutional:", "r:foo bar"):
            with self.subTest(remote=remote):
                with patch.object(tool, "dump_from_railway_ascii") as export:
                    with self.assertRaises(tool.SafeBlocker):
                        tool.run_backup(private_root=self.root, remote=remote,
                                        recipient=RECIPIENT, railway="railway",
                                        age="age", rclone="rclone")
                    export.assert_not_called()

    def test_invalid_recipient_rejected_without_export(self):
        for recipient in ("age-secret-key-1wrong", "", "age1invalid recipient", "age1q;"):
            with self.subTest(recipient=recipient):
                with patch.object(tool, "dump_from_railway_ascii") as export:
                    with self.assertRaisesRegex(tool.SafeBlocker, "C1_AGE_RECIPIENT_INVALID"):
                        tool.run_backup(private_root=self.root, remote=self.remote,
                                        recipient=recipient, railway="railway",
                                        age="age", rclone="rclone")
                    export.assert_not_called()

    def test_age_failure_cleans_plaintext_and_prevents_upload(self):
        def fail_encrypt(args, *, failure, timeout=900):
            if args[0] == "age-test":
                raise tool.SafeBlocker("C1_AGE_ENCRYPT_FAILED")
            return self.command(args, failure=failure, timeout=timeout)
        with patch.object(tool, "dump_from_railway_ascii", side_effect=self.export), \
                patch.object(tool, "safe_run", side_effect=fail_encrypt), \
                patch.object(tool, "remote_cipher_digest") as reader:
            with self.assertRaisesRegex(tool.SafeBlocker, "C1_AGE_ENCRYPT_FAILED"):
                tool.run_backup(private_root=self.root, remote=self.remote,
                                recipient=RECIPIENT, railway="railway-test",
                                age="age-test", rclone="rclone-test")
            reader.assert_not_called()
        self.assertEqual(list((self.root / "private-backups").glob("**/*.dump")), [])

    def test_missing_cloud_access_blocks_before_export(self):
        def refuse(args, *, failure, timeout=900):
            raise tool.SafeBlocker(failure)
        with patch.object(tool, "safe_run", side_effect=refuse), \
                patch.object(tool, "dump_from_railway_ascii") as export:
            with self.assertRaisesRegex(tool.SafeBlocker, "C1_INSTITUTIONAL_DRIVE_NOT_READY"):
                tool.run_backup(private_root=self.root, remote=self.remote,
                                recipient=RECIPIENT, railway="railway-test",
                                age="age-test", rclone="rclone-test")
            export.assert_not_called()


if __name__ == "__main__":
    unittest.main()
