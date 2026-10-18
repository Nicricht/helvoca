"""C1 offline, fail-closed copy/crypto/restore contract. NO real secrets or provider access."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
import sys
sys.path.insert(0, str(HERE))
SPEC = importlib.util.spec_from_file_location("c1", HERE / "c1_offsite_pg18_encrypted_restore.py")
c1 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(c1)


def fake_restored(*args):
    return {"public_tables": 89, "core_tables": 5, "flyway_history_rows": 99}


class C1OffsiteBackupTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.private = self.root / "LocalAppData" / "Helvoca"
        self.sources = self.private / "private-backups"
        self.sources.mkdir(parents=True)
        self.external = self.root / "External-Drive"
        self.external.mkdir()
        self.source = self.sources / c1.ARCHIVE
        self.payload = b"PGDMP" + bytes(range(256)) * 12
        self.source.write_bytes(self.payload)
        self.pinned = hashlib.sha256(self.payload).hexdigest()
        self.patcher = patch.object(c1, "EXPECTED_SHA256", self.pinned)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)
        self.age_calls = []
        self.restore_calls = []

    def simulate_age(self, argv, check, timeout):
        self.age_calls.append(argv)
        output = Path(argv[argv.index("--output") + 1])
        inp = Path(argv[-1])
        if "--passphrase" in argv:
            output.write_bytes(c1.AGE_HEADER + inp.read_bytes()[::-1])
        elif "--decrypt" in argv:
            raw = inp.read_bytes()
            if not raw.startswith(c1.AGE_HEADER):
                return subprocess.CompletedProcess(argv, 2)
            output.write_bytes(raw[len(c1.AGE_HEADER):][::-1])
        else:
            raise AssertionError("unexpected age mode")
        return subprocess.CompletedProcess(argv, 0)

    def restore(self, docker, clear):
        self.restore_calls.append((docker, clear, clear.read_bytes()))
        return fake_restored()

    def certify(self):
        with patch.object(c1.subprocess, "run", side_effect=self.simulate_age), \
             patch.object(c1, "restore_to_disposable_docker", side_effect=self.restore):
            return c1.seal_and_verify(self.source, self.external, "age", "docker",
                                      local_root=self.private)

    def test_reuses_exact_existing_source_without_railway_or_dump_call(self):
        report = self.certify()
        self.assertEqual("OFFSITE_CIPHERTEXT_DECRYPT_AND_ISOLATED_PG18_RESTORE_PASSED",
                         report["status"])
        self.assertEqual(self.pinned, report["source_sha256"])
        self.assertEqual(self.payload, self.restore_calls[0][2])
        self.assertEqual("docker", self.restore_calls[0][0])
        self.assertEqual(["age", "--passphrase"], self.age_calls[0][:2])
        self.assertEqual(["age", "--decrypt"], self.age_calls[1][:2])
        self.assertNotIn("railway", repr(self.age_calls))
        self.assertEqual(5, report["isolated_pg18_core_tables"])
        self.assertTrue(report["isolated_restore_seconds_measured"] >= 0)
        self.assertFalse(report["production_modified"])
        self.assertFalse(report["commercial_go"])

    def test_ciphertext_is_independent_file_and_no_cleartext_remains(self):
        report = self.certify()
        encrypted = self.external / (c1.ARCHIVE + ".age")
        self.assertTrue(encrypted.is_file())
        self.assertTrue(encrypted.read_bytes().startswith(c1.AGE_HEADER))
        self.assertFalse(encrypted.read_bytes().startswith(b"PGDMP"))
        self.assertEqual(hashlib.sha256(encrypted.read_bytes()).hexdigest(),
                         report["encrypted_sha256"])
        self.assertFalse(list(self.private.rglob("restored-from-offsite.dump")))
        self.assertFalse(list(self.external.glob("*.partial")))
        self.assertTrue(self.source.exists())

    def test_never_claims_sync_passphrase_custody_or_production_rpo_rto(self):
        report = self.certify()
        for key in ("independent_storage_verified",
                    "remote_sync_or_offsite_durability_verified",
                    "operator_has_separately_stored_decryption_passphrase_verified"):
            self.assertIs(report[key], False)
        self.assertIsNone(report["measured_production_rpo"])
        self.assertIsNone(report["measured_production_rto"])

    def test_rejects_wrong_hash_before_any_encrypt(self):
        self.source.write_bytes(b"PGDMP" + b"tamper" * 400)
        with patch.object(c1.subprocess, "run", side_effect=self.simulate_age):
            with self.assertRaisesRegex(c1.SafeBlocker, "SOURCE_SHA256_MISMATCH"):
                c1.seal_and_verify(self.source, self.external, "age", "docker",
                                   local_root=self.private)
        self.assertEqual([], self.age_calls)

    def test_rejects_non_postgres_dump_before_any_encrypt(self):
        self.source.write_bytes(b"not-a-postgres-dump" + b"x" * 2048)
        with self.assertRaisesRegex(c1.SafeBlocker, "ARCHIVE_NOT_PGDMP"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)

    def test_rejects_backup_too_small(self):
        self.source.write_bytes(b"PGDMP")
        with self.assertRaisesRegex(c1.SafeBlocker, "ARCHIVE_TOO_SMALL"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)

    def test_never_overwrites_existing_offsite_archive(self):
        encrypted = self.external / (c1.ARCHIVE + ".age")
        encrypted.write_bytes(b"do not overwrite prior backup")
        with self.assertRaisesRegex(c1.SafeBlocker, "OFFSITE_ARCHIVE_ALREADY_EXISTS"):
            self.certify()
        self.assertEqual(b"do not overwrite prior backup", encrypted.read_bytes())
        self.assertEqual([], self.age_calls)

    def test_rejects_source_directory_as_offsite(self):
        with self.assertRaisesRegex(c1.SafeBlocker, "OFFSITE_EQUALS_SOURCE_DIRECTORY"):
            c1.seal_and_verify(self.source, self.sources, "age", "docker",
                               local_root=self.private)

    def test_rejects_nested_offsite(self):
        nested = self.sources / "another"
        nested.mkdir()
        with self.assertRaisesRegex(c1.SafeBlocker, "OFFSITE_NESTED_WITH_SOURCE"):
            c1.seal_and_verify(self.source, nested, "age", "docker",
                               local_root=self.private)

    def test_rejects_git_output(self):
        (self.external / ".git").mkdir()
        with self.assertRaisesRegex(c1.SafeBlocker, "GIT_DIRECTORY_REJECTED"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)

    @unittest.skipIf(os.name == "nt", "symlink privileges vary on Windows")
    def test_rejects_symlink_in_destination_chain(self):
        external_link = self.root / "external-link"
        external_link.symlink_to(self.external, target_is_directory=True)
        with self.assertRaisesRegex(c1.SafeBlocker, "SYMLINK_DIRECTORY_REJECTED"):
            c1.seal_and_verify(self.source, external_link, "age", "docker",
                               local_root=self.private)

    def test_encrypt_failure_does_not_report_success_or_leave_ciphertext(self):
        def fail(*args, **kwargs):
            return subprocess.CompletedProcess(args, 1)
        with patch.object(c1.subprocess, "run", side_effect=fail), \
             self.assertRaisesRegex(c1.SafeBlocker, "AGE_ENCRYPT_FAILED"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)
        self.assertFalse((self.external / (c1.ARCHIVE + ".age")).exists())

    def test_decrypt_failure_never_attempts_restore(self):
        def fail_decrypt(argv, check, timeout):
            if "--decrypt" in argv:
                return subprocess.CompletedProcess(argv, 1)
            return self.simulate_age(argv, check, timeout)
        with patch.object(c1.subprocess, "run", side_effect=fail_decrypt), \
             patch.object(c1, "restore_to_disposable_docker", side_effect=self.restore), \
             self.assertRaisesRegex(c1.SafeBlocker, "AGE_DECRYPT_FAILED"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)
        self.assertFalse(self.restore_calls)
        self.assertFalse(list(self.private.rglob("restored-from-offsite.dump")))

    def test_tampered_ciphertext_cannot_restore(self):
        def bad_decrypt(argv, check, timeout):
            if "--decrypt" in argv:
                Path(argv[argv.index("--output") + 1]).write_bytes(b"PGDMP" + b"x" * 2600)
                return subprocess.CompletedProcess(argv, 0)
            return self.simulate_age(argv, check, timeout)
        with patch.object(c1.subprocess, "run", side_effect=bad_decrypt), \
             patch.object(c1, "restore_to_disposable_docker", side_effect=self.restore), \
             self.assertRaisesRegex(c1.SafeBlocker, "SOURCE_SHA256_MISMATCH"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)
        self.assertFalse(self.restore_calls)

    def test_pg18_restore_failure_never_attests_go(self):
        with patch.object(c1.subprocess, "run", side_effect=self.simulate_age), \
             patch.object(c1, "restore_to_disposable_docker",
                          side_effect=RuntimeError("secret DB record from failure")), \
             self.assertRaisesRegex(RuntimeError, "secret DB record"):
            c1.seal_and_verify(self.source, self.external, "age", "docker",
                               local_root=self.private)
        self.assertFalse(list(self.private.rglob("restored-from-offsite.dump")))

    def test_local_report_creates_private_random_file_no_secret_fields(self):
        report = self.certify()
        path = c1.write_private_report(report, self.private)
        self.assertTrue(path.is_file())
        saved = json.loads(path.read_text())
        self.assertEqual(report, saved)
        for forbidden in ("password", "passphrase", "token", "phone_number", "customer"):
            self.assertNotIn(forbidden, saved)
        self.assertTrue(path.name.endswith(".private.json"))

    def test_missing_external_destination_is_blocked(self):
        with self.assertRaisesRegex(c1.SafeBlocker, "MISSING_OR_UNSAFE_DIRECTORY"):
            c1.seal_and_verify(self.source, self.root / "does-not-exist", "age", "docker",
                               local_root=self.private)

    def test_relative_directory_rejected(self):
        with self.assertRaisesRegex(c1.SafeBlocker, "RELATIVE_DIRECTORY_REJECTED"):
            c1.secure_directory(Path("c1-relative"), create=False)

    def test_missing_age_does_not_start_any_crypto_or_carrier_action(self):
        with patch.dict(os.environ, {"LOCALAPPDATA": str(self.private.parent)}), \
             patch.object(c1.shutil, "which", side_effect=lambda x: None if x == "age" else "docker"), \
             patch("builtins.print") as printer:
            self.assertEqual(2, c1.main(["--offsite-dir", str(self.external)]))
            self.assertIn("AGE_CLI_MISSING", str(printer.call_args_list))
        self.assertEqual([], self.age_calls)

    def test_no_secret_passphrase_in_command_arguments(self):
        self.certify()
        arguments = repr(self.age_calls).lower()
        self.assertIn("--passphrase", arguments)
        self.assertNotIn("mypassword", arguments)
        self.assertNotIn("railway", arguments)
        self.assertNotIn("twilio", arguments)


if __name__ == "__main__":
    unittest.main()
