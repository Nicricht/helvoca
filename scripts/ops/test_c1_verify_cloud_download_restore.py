"""Offline adversarial tests: never contact Railway, Google, age or Docker."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import c1_verify_cloud_download_restore as c1

DUMP = b"PGDMP" + b"synthetic-c1-restore" * 120


class CloudRestoreTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name) / "private"
        self.evidence = self.root / "private-evidence"
        self.evidence.mkdir(parents=True)
        self.download_dir = Path(temp.name) / "fresh-download"
        self.download_dir.mkdir()
        self.ciphertext = self.download_dir / c1.CLOUD_ARCHIVE_NAME
        self.blob = c1.AGE_HEADER + b"synthetic-age-ciphertext" * 55
        self.ciphertext.write_bytes(self.blob)
        self.report = self.evidence / "capture.private.json"
        self.metadata = {
            "status": c1.ORIGINAL_PASS_STATUS,
            "encrypted_sha256": hashlib.sha256(self.blob).hexdigest(),
            "encrypted_bytes": len(self.blob),
            "source_sha256": hashlib.sha256(DUMP).hexdigest(),
            "source_bytes": len(DUMP),
        }
        self.report.write_text(json.dumps(self.metadata), encoding="utf-8")
        for key, value in (
            ("CLOUD_ENCRYPTED_BYTES", len(self.blob)),
            ("CLOUD_SHA256", hashlib.sha256(self.blob).hexdigest()),
        ):
            handle = patch.object(c1, key, value)
            handle.start()
            self.addCleanup(handle.stop)

    def assert_blocked(self, code, *, downloaded=None, report=None):
        with self.assertRaises(c1.SafeBlocker) as error:
            c1.verified_inputs(downloaded or self.ciphertext,
                               report or self.report, self.root)
        self.assertEqual(str(error.exception), code)

    def test_valid_pinned_download_and_private_report(self):
        self.assertEqual(c1.verified_inputs(self.ciphertext, self.report, self.root),
                         {"bytes": len(DUMP), "sha256": self.metadata["source_sha256"]})

    def test_absolute_input_required(self):
        self.assert_blocked("C1_ABSOLUTE_PATHS_REQUIRED", downloaded=Path("relative.age"))
        self.assert_blocked("C1_ABSOLUTE_PATHS_REQUIRED", report=Path("relative.json"))

    def test_filename_pinned(self):
        alternate = self.download_dir / "untrusted.dump.age"
        alternate.write_bytes(self.blob)
        self.assert_blocked("C1_CLOUD_ARCHIVE_ID_MISMATCH", downloaded=alternate)

    def test_missing_and_symlinked_ciphertext(self):
        self.ciphertext.unlink()
        self.assert_blocked("C1_CLOUD_FILE_UNSAFE")
        self.ciphertext.symlink_to(self.report)
        self.assert_blocked("C1_CLOUD_FILE_UNSAFE")

    def test_unsafe_parent_inside_git(self):
        (self.download_dir / ".git").mkdir()
        self.assert_blocked("GIT_DIRECTORY_REJECTED")

    def test_missing_and_symlinked_private_report(self):
        self.report.unlink()
        self.assert_blocked("C1_PRIVATE_REPORT_UNSAFE")
        self.report.symlink_to(self.ciphertext)
        self.assert_blocked("C1_PRIVATE_REPORT_UNSAFE")

    def test_report_not_in_private_evidence(self):
        elsewhere = self.root / "report.private.json"
        elsewhere.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_REPORT_OUTSIDE_PRIVATE_EVIDENCE", report=elsewhere)

    def test_size_must_match_remote_metadata(self):
        self.ciphertext.write_bytes(self.blob[:-1])
        self.assert_blocked("C1_CLOUD_ARCHIVE_SIZE_MISMATCH")

    def test_sha_must_match_remote_observed_digest(self):
        self.ciphertext.write_bytes(self.blob[:-1] + b"0")
        self.assert_blocked("C1_CLOUD_ARCHIVE_SHA_MISMATCH")

    def test_header_checked_even_when_sha_matches(self):
        corrupted = b"BROKEN" + self.blob[6:]
        self.ciphertext.write_bytes(corrupted)
        with patch.object(c1, "CLOUD_SHA256", hashlib.sha256(corrupted).hexdigest()):
            self.assert_blocked("C1_CLOUD_AGE_HEADER_INVALID")

    def test_json_malformed_or_undecodable(self):
        self.report.write_text("{", encoding="utf-8")
        self.assert_blocked("C1_PRIVATE_REPORT_INVALID")
        self.report.write_bytes(b"\xff")
        self.assert_blocked("C1_PRIVATE_REPORT_INVALID")

    def test_report_must_be_verified_backup_and_object(self):
        self.report.write_text('[]')
        self.assert_blocked("C1_LOCAL_RESTORE_ATTESTATION_MISSING")
        self.metadata["status"] = "FAKE_PASS"
        self.report.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_LOCAL_RESTORE_ATTESTATION_MISSING")

    def test_report_cipher_digest_and_size_must_match(self):
        self.metadata["encrypted_sha256"] = "0" * 64
        self.report.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_LOCAL_CLOUD_CIPHERTEXT_MISMATCH")
        self.metadata["encrypted_sha256"] = hashlib.sha256(self.blob).hexdigest()
        self.metadata["encrypted_bytes"] = True
        self.report.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_LOCAL_CLOUD_CIPHERTEXT_MISMATCH")

    def test_source_sha_and_length_are_validated(self):
        self.metadata["source_sha256"] = "not-a-sha"
        self.report.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_PRIVATE_SOURCE_SHA_INVALID")
        self.metadata["source_sha256"] = hashlib.sha256(DUMP).hexdigest()
        self.metadata["source_bytes"] = 3
        self.report.write_text(json.dumps(self.metadata))
        self.assert_blocked("C1_PRIVATE_SOURCE_SIZE_INVALID")

    def restore(self, *, dump=DUMP, docker_error=None, age_error=None):
        observed = []
        def simulate_age(_exe, args, *, code):
            self.assertEqual(code, "C1_CLOUD_DECRYPT_FAILED")
            self.assertEqual(args[0], "--decrypt")
            self.assertEqual(args[-1], str(self.ciphertext))
            observed.append("age")
            if age_error:
                raise age_error
            Path(args[args.index("--output") + 1]).write_bytes(dump)
        def simulate_pg(_exe, clear):
            observed.append("pg")
            self.assertEqual(clear.read_bytes(), dump)
            if docker_error:
                raise docker_error
            return {"public_tables": 7, "core_tables": 6, "flyway_history_rows": 14}
        with patch.object(c1, "require_docker_engine") as engine, \
             patch.object(c1, "run_age", side_effect=simulate_age), \
             patch.object(c1, "restore_to_disposable_docker", side_effect=simulate_pg):
            def perform():
                return c1.restore_downloaded(self.ciphertext, self.report,
                                            self.root, "age-mock", "docker-mock")
            return perform, observed, engine

    def test_offline_restore_and_private_temp_cleanup(self):
        self.assertEqual(c1.verified_inputs(self.ciphertext, self.report, self.root)["bytes"],
                         len(DUMP))
        def simulate_age(exe, args, *, code):
            self.assertEqual(exe, "age-mock")
            self.assertEqual(code, "C1_CLOUD_DECRYPT_FAILED")
            Path(args[args.index("--output") + 1]).write_bytes(DUMP)
        with patch.object(c1, "require_docker_engine") as engine, \
             patch.object(c1, "run_age", side_effect=simulate_age), \
             patch.object(c1, "restore_to_disposable_docker",
                          return_value={"public_tables": 7, "core_tables": 6,
                                        "flyway_history_rows": 14}) as pg:
            report = c1.restore_downloaded(self.ciphertext, self.report, self.root,
                                           "age-mock", "docker-mock")
        engine.assert_called_once_with("docker-mock")
        pg.assert_called_once()
        self.assertFalse(pg.call_args.args[1].exists())
        self.assertEqual(report["status"], "C1_CLOUD_MATCHING_CIPHERTEXT_PG18_ISOLATED_RESTORE_PASSED")
        self.assertFalse(report["production_modified"])
        self.assertFalse(report["commercial_go"])
        self.assertIsNone(report["production_rpo_measured"])
        self.assertIsNone(report["production_rto_measured"])
        self.assertFalse(report["operator_independent_download_origin_verified_automatically"])
        self.assertEqual(report["isolated_pg18_core_tables"], 6)

    def test_invalid_decrypted_pgdmp_blocked(self):
        with patch.object(c1, "require_docker_engine"), \
             patch.object(c1, "run_age",
                          side_effect=lambda exe, args, *, code: Path(args[args.index("--output") + 1]).write_bytes(b"junk")), \
             patch.object(c1, "restore_to_disposable_docker") as pg:
            with self.assertRaisesRegex(c1.SafeBlocker, "C1_CLOUD_DECRYPTED_DUMP_INVALID"):
                c1.restore_downloaded(self.ciphertext, self.report, self.root, "age", "docker")
        pg.assert_not_called()

    def test_wrong_plaintext_sha_blocks_pg_restore(self):
        tampered = DUMP[:-1] + b"Y"
        with patch.object(c1, "require_docker_engine"), \
             patch.object(c1, "run_age",
                          side_effect=lambda exe, args, *, code: Path(args[args.index("--output") + 1]).write_bytes(tampered)), \
             patch.object(c1, "restore_to_disposable_docker") as pg:
            with self.assertRaisesRegex(c1.SafeBlocker, "C1_CLOUD_SOURCE_DUMP_MISMATCH"):
                c1.restore_downloaded(self.ciphertext, self.report, self.root, "age", "docker")
        pg.assert_not_called()

    def test_age_error_fails_closed_and_does_not_restore(self):
        with patch.object(c1, "require_docker_engine"), \
             patch.object(c1, "run_age", side_effect=c1.SafeBlocker("C1_CLOUD_DECRYPT_FAILED")), \
             patch.object(c1, "restore_to_disposable_docker") as pg:
            with self.assertRaisesRegex(c1.SafeBlocker, "C1_CLOUD_DECRYPT_FAILED"):
                c1.restore_downloaded(self.ciphertext, self.report, self.root, "age", "docker")
        pg.assert_not_called()

    def test_docker_restore_failure_is_safe_code(self):
        with patch.object(c1, "require_docker_engine"), \
             patch.object(c1, "run_age", side_effect=lambda exe, args, *, code: Path(args[args.index("--output") + 1]).write_bytes(DUMP)), \
             patch.object(c1, "restore_to_disposable_docker", side_effect=RuntimeError("PRIVATE ERROR")):
            with self.assertRaisesRegex(c1.SafeBlocker, "C1_CLOUD_PG18_RESTORE_FAILED"):
                c1.restore_downloaded(self.ciphertext, self.report, self.root, "age", "docker")

    def test_cli_missing_age_or_docker(self):
        args = ["--downloaded-ciphertext", str(self.ciphertext),
                "--private-report", str(self.report), "--private-root", str(self.root)]
        with patch.object(c1.shutil, "which", return_value=None):
            self.assertEqual(c1.main(args), 2)
        with patch.object(c1.shutil, "which", side_effect=["age", None]):
            self.assertEqual(c1.main(args), 2)

    def test_cli_success_writes_private_result_without_sensitive_output(self):
        args = ["--downloaded-ciphertext", str(self.ciphertext),
                "--private-report", str(self.report), "--private-root", str(self.root)]
        with patch.object(c1.shutil, "which", side_effect=["age", "docker"]), \
             patch.object(c1, "restore_downloaded", return_value={"status": "PASS"}), \
             patch.object(c1, "write_private_report", return_value=self.evidence / "result.json") as save:
            self.assertEqual(c1.main(args), 0)
        save.assert_called_once()

    def test_cli_generic_private_exception_returns_safe_code(self):
        args = ["--downloaded-ciphertext", str(self.ciphertext),
                "--private-report", str(self.report), "--private-root", str(self.root)]
        with patch.object(c1.shutil, "which", side_effect=["age", "docker"]), \
             patch.object(c1, "restore_downloaded", side_effect=OSError("SECRET")):
            self.assertEqual(c1.main(args), 2)

    def test_cli_safe_blocker_returns_no_success(self):
        args = ["--downloaded-ciphertext", str(self.ciphertext),
                "--private-report", str(self.report), "--private-root", str(self.root)]
        with patch.object(c1.shutil, "which", side_effect=["age", "docker"]), \
             patch.object(c1, "restore_downloaded", side_effect=c1.SafeBlocker("SAFE_CODE")):
            self.assertEqual(c1.main(args), 2)


if __name__ == "__main__":
    unittest.main()
