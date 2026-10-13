#!/usr/bin/env python3
"""Synthetic C1 regression tests, never reach Railway, Twilio or real Docker."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import c1_replace_missing_offsite_archive as tool

DUMP = b"PGDMP" + b"synthetic-data" * 200


class ReplacementBackupTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "private" / "Helvoca"
        self.offsite = Path(self.tmp.name) / "offsite"
        self.offsite.mkdir()
        self.observed = []

    def export(self, railway, path):
        self.assertEqual(railway, "railway-test")
        self.assertTrue(str(path).startswith(str(self.root / "private-backups")))
        self.assertFalse(str(path).startswith(str(self.offsite)))
        self.observed.append(("export", path))
        path.write_bytes(DUMP)
        return {"bytes": len(DUMP), "sha256": hashlib.sha256(DUMP).hexdigest()}

    def age(self, exe, args, *, code):
        self.assertEqual(exe, "age-test")
        self.assertNotIn("--identity", args)
        self.assertNotIn("--recipient", args)
        self.assertNotIn("--password", args)
        self.observed.append(("age", code))
        target = Path(args[args.index("--output") + 1])
        source = Path(args[-1])
        payload = source.read_bytes()
        if "--passphrase" in args:
            self.assertEqual(code, "AGE_ENCRYPT_FAILED")
            self.assertTrue(target.is_relative_to(self.offsite))
            self.assertEqual(payload, DUMP)
            target.write_bytes(tool.AGE_HEADER + payload)
        else:
            self.assertEqual(code, "AGE_DECRYPT_FAILED")
            self.assertTrue(source.is_relative_to(self.offsite))
            self.assertTrue(target.is_relative_to(self.root / "private-evidence"))
            target.write_bytes(payload[len(tool.AGE_HEADER):])

    def restore(self, exe, dump):
        self.assertEqual(exe, "docker-test")
        self.assertEqual(dump.read_bytes(), DUMP)
        self.assertTrue(dump.is_relative_to(self.root / "private-evidence"))
        self.observed.append(("isolated_restore", True))
        return {"public_tables": 9, "core_tables": 5, "flyway_history_rows": 99}

    def call(self):
        with patch.object(tool, "dump_from_railway", side_effect=self.export), \
                patch.object(tool, "run_age", side_effect=self.age), \
                patch.object(tool, "restore_to_disposable_docker", side_effect=self.restore):
            return tool.backup_encrypt_and_verify(self.offsite, "railway-test", "age-test",
                                                  "docker-test", local_root=self.root)

    def test_new_export_ciphers_offsite_decrypts_and_restores_in_isolation(self):
        report = self.call()
        self.assertEqual(report["source_sha256"], hashlib.sha256(DUMP).hexdigest())
        self.assertFalse(report["remote_sync_or_offsite_durability_verified"])
        self.assertFalse(report["commercial_go"])
        self.assertFalse(report["production_modified"])
        self.assertIsNone(report["measured_production_rto"])
        self.assertEqual([i[0] for i in self.observed],
                         ["export", "age", "age", "isolated_restore"])
        self.assertEqual(len(list(self.offsite.iterdir())), 1)
        stored = next(self.offsite.iterdir())
        self.assertTrue(stored.name.endswith(".dump.age"))
        self.assertEqual(stored.read_bytes(), tool.AGE_HEADER + DUMP)
        self.assertEqual(len(list((self.root / "private-backups").glob("*.dump"))), 1)
        self.assertEqual(list((self.root / "private-evidence").glob("*.dump")), [])

    def test_no_export_if_offsite_does_not_exist(self):
        self.offsite.rmdir()
        with patch.object(tool, "dump_from_railway") as export:
            with self.assertRaises(tool.SafeBlocker):
                tool.backup_encrypt_and_verify(self.offsite, "railway", "age", "docker",
                                               local_root=self.root)
            export.assert_not_called()

    def test_nested_offsite_not_allowed(self):
        nested = self.root / "private-backups" / "cloud"
        nested.mkdir(parents=True)
        with patch.object(tool, "dump_from_railway") as export:
            with self.assertRaises(tool.SafeBlocker):
                tool.backup_encrypt_and_verify(nested, "railway", "age", "docker",
                                               local_root=self.root)
            export.assert_not_called()

    def test_failed_encryption_never_reports_success_or_writes_plain_offsite(self):
        def reject(*args, **kwargs):
            raise tool.SafeBlocker("AGE_ENCRYPT_FAILED")
        with patch.object(tool, "dump_from_railway", side_effect=self.export), \
                patch.object(tool, "run_age", side_effect=reject), \
                patch.object(tool, "restore_to_disposable_docker") as restore:
            with self.assertRaisesRegex(tool.SafeBlocker, "AGE_ENCRYPT_FAILED"):
                tool.backup_encrypt_and_verify(self.offsite, "railway-test", "age-test",
                                               "docker-test", local_root=self.root)
            restore.assert_not_called()
        self.assertEqual(list(self.offsite.iterdir()), [])

    def test_modified_decrypted_archive_cannot_count_as_success(self):
        def corrupt(exe, args, *, code):
            self.age(exe, args, code=code)
            if "--decrypt" in args:
                target = Path(args[args.index("--output") + 1])
                damaged = target.read_bytes()
                target.write_bytes(damaged[:-1] + bytes([damaged[-1] ^ 1]))
        with patch.object(tool, "dump_from_railway", side_effect=self.export), \
                patch.object(tool, "run_age", side_effect=corrupt), \
                patch.object(tool, "restore_to_disposable_docker") as restore:
            with self.assertRaisesRegex(tool.SafeBlocker, "RESTORED_DUMP_CHECKSUM_MISMATCH"):
                tool.backup_encrypt_and_verify(self.offsite, "railway-test", "age-test",
                                               "docker-test", local_root=self.root)
            restore.assert_not_called()

    def test_missing_tools_fail_closed_before_any_external_action(self):
        with patch.object(tool.shutil, "which", side_effect=lambda name: None if name == "railway" else "mock"), \
                patch.object(tool, "backup_encrypt_and_verify") as op:
            self.assertEqual(tool.main(["--offsite-dir", str(self.offsite)]), 2)
            op.assert_not_called()


if __name__ == "__main__":
    unittest.main()
