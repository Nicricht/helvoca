#!/usr/bin/env python3
"""Offline synthetic tests for recovery from Drive-downloaded ciphertext."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import c1_independent_remote_restore as tool


DUMP = b"PGDMP" + b"fake-testing-only" * 170
CIPHER = tool.AGE_HEADER + DUMP + b"TEST"
CIPHER_SHA = hashlib.sha256(CIPHER).hexdigest()


class BrowserRemoteRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.local = Path(self.tmp.name) / "HelvocaPrivate"
        self.downloads = Path(self.tmp.name) / "Downloads"
        self.downloads.mkdir()
        self.original = self.downloads / tool.REMOTE_FILE
        self.original.write_bytes(CIPHER)
        self.source_sha = hashlib.sha256(DUMP).hexdigest()
        self.local_evidence = self.local / "private-evidence"
        self.local_evidence.mkdir(parents=True)
        (self.local_evidence / "helvoca-c1-offsite-test.private.json").write_text(json.dumps({
            "encrypted_sha256": CIPHER_SHA,
            "encrypted_bytes": len(CIPHER),
            "source_sha256": self.source_sha,
            "source_bytes": len(DUMP),
            "status": tool.SOURCE_REPORT_STATUS,
        }))
        self.remote = patch.object(tool, "REMOTE_ENCRYPTED_SHA256", CIPHER_SHA)
        self.length = patch.object(tool, "REMOTE_ENCRYPTED_BYTES", len(CIPHER))
        self.remote.start()
        self.length.start()
        self.addCleanup(self.remote.stop)
        self.addCleanup(self.length.stop)

    def test_verified_browser_download_restores_without_any_railway_connection(self):
        observed = []
        def local_decrypt(exe, args, *, code):
            self.assertEqual(exe, "age-test")
            self.assertEqual(code, "RECOVERY_AGE_DECRYPT_FAILED")
            self.assertNotIn("--passphrase", args)
            self.assertEqual(args[0], "--decrypt")
            self.assertEqual(Path(args[-1]), self.original)
            clear = Path(args[args.index("--output") + 1])
            self.assertTrue(clear.is_relative_to(self.local / "private-evidence"))
            clear.write_bytes(DUMP)
            observed.append("decrypt")
        def isolated_restore(exe, path):
            self.assertEqual(exe, "docker-test")
            self.assertEqual(path.read_bytes(), DUMP)
            self.assertTrue(path.is_relative_to(self.local / "private-evidence"))
            observed.append("restore")
            return {"core_tables": 5, "public_tables": 20, "flyway_history_rows": 105}

        with patch.object(tool, "run_age", side_effect=local_decrypt), \
                patch.object(tool, "restore_to_disposable_docker", side_effect=isolated_restore), \
                patch.object(tool, "require_docker_engine"), \
                patch.object(tool, "write_private_report"):
            result = tool.restore_independent_download(self.original,
                                                        private_root=self.local,
                                                        age="age-test", docker="docker-test")
        self.assertEqual(observed, ["decrypt", "restore"])
        self.assertEqual(result["remote_encrypted_sha256"], CIPHER_SHA)
        self.assertEqual(result["decrypted_source_sha256_verified"], self.source_sha)
        self.assertFalse(result["commercial_go"])
        self.assertFalse(result["complete_production_rto_verified"])
        self.assertFalse(result["operator_passphrase_custody_verified"])
        self.assertEqual(list(self.local_evidence.glob("**/*.dump")), [])
        self.assertTrue(self.original.exists())

    def test_modified_ciphertext_rejected_before_decrypt(self):
        self.original.write_bytes(CIPHER[:-1] + b"X")
        with patch.object(tool, "run_age") as age, patch.object(tool, "restore_to_disposable_docker") as restore:
            with self.assertRaisesRegex(tool.SafeBlocker, "REMOTE_DOWNLOAD_SHA256_MISMATCH"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age-test", docker="docker-test")
            age.assert_not_called()
            restore.assert_not_called()

    def test_truncated_download_rejected_before_decrypt(self):
        self.original.write_bytes(CIPHER[:50])
        with patch.object(tool, "run_age") as age:
            with self.assertRaisesRegex(tool.SafeBlocker, "REMOTE_DOWNLOAD_SIZE_MISMATCH"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            age.assert_not_called()

    def test_missing_download_rejected_before_decrypt(self):
        self.original.unlink()
        with patch.object(tool, "run_age") as age:
            with self.assertRaisesRegex(tool.SafeBlocker, "REMOTE_DOWNLOAD_MISSING_OR_SYMLINK"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            age.assert_not_called()

    def test_original_evidence_missing_rejected(self):
        next(self.local_evidence.glob("*.json")).unlink()
        with patch.object(tool, "run_age") as age:
            with self.assertRaisesRegex(tool.SafeBlocker, "ORIGINAL_BACKUP_EVIDENCE_NOT_UNIQUE"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            age.assert_not_called()

    def test_original_evidence_duplicate_rejected(self):
        file = next(self.local_evidence.glob("*.json"))
        (self.local_evidence / "helvoca-c1-offsite-duplicate.private.json").write_text(file.read_text())
        with patch.object(tool, "run_age") as age:
            with self.assertRaisesRegex(tool.SafeBlocker, "ORIGINAL_BACKUP_EVIDENCE_NOT_UNIQUE"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            age.assert_not_called()

    def test_wrong_source_checksum_never_reaches_restore(self):
        def local_decrypt(exe, args, *, code):
            Path(args[args.index("--output") + 1]).write_bytes(b"PGDMP" + b"X" * (len(DUMP)-5))
        with patch.object(tool, "run_age", side_effect=local_decrypt), \
                patch.object(tool, "require_docker_engine"), \
                patch.object(tool, "restore_to_disposable_docker") as restore:
            with self.assertRaisesRegex(tool.SafeBlocker, "RECOVERY_ORIGINAL_DUMP_SHA256_MISMATCH"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            restore.assert_not_called()
        self.assertEqual(list(self.local_evidence.glob("**/*.dump")), [])

    def test_unsupported_source_inside_local_private_root_rejected(self):
        unsafe = self.local / "private-evidence" / tool.REMOTE_FILE
        unsafe.write_bytes(CIPHER)
        with self.assertRaisesRegex(tool.SafeBlocker, "REMOTE_DOWNLOAD_INSIDE_LOCAL_PRIVATE_ROOT"):
            tool.verify_downloaded_ciphertext(unsafe, self.local)

    def test_broken_age_decrypt_cleans_local_cleartext(self):
        def broken_age(exe, args, *, code):
            Path(args[args.index("--output") + 1]).write_bytes(b"PGDMPSECRET")
            raise tool.SafeBlocker("RECOVERY_AGE_DECRYPT_FAILED")
        with patch.object(tool, "run_age", side_effect=broken_age), \
                patch.object(tool, "require_docker_engine"), \
                patch.object(tool, "restore_to_disposable_docker") as restore:
            with self.assertRaisesRegex(tool.SafeBlocker, "RECOVERY_AGE_DECRYPT_FAILED"):
                tool.restore_independent_download(self.original, private_root=self.local,
                                                  age="age", docker="docker")
            restore.assert_not_called()
        self.assertEqual(list(self.local_evidence.glob("**/*.dump")), [])

    def test_cli_does_not_require_railway_tool_or_network(self):
        with patch.object(tool.shutil, "which", side_effect=lambda x: None if x == "age" else "docker"), \
                patch.object(tool, "restore_independent_download") as restore:
            self.assertEqual(tool.main([]), 2)
            restore.assert_not_called()


if __name__ == "__main__":
    unittest.main()
