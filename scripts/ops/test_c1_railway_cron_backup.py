#!/usr/bin/env python3
"""Synthetic C1 Railway-native runner tests. Never connect to providers."""
from pathlib import Path
import hashlib
import tempfile
import unittest
from unittest.mock import patch

import c1_railway_cron_backup as job

PGDMP = b"PGDMP" + (b"FAKE_OFFLINE_RECORDS" * 300)
SEALED = job.AGE_HEADER + PGDMP + b"CIPHER_FAKE_TRAILER"
RECIPIENT = "age1" + "q" * 58


def settings():
    return {
        "PGHOST": "postgres.railway.internal",
        "PGPORT": "5432",
        "PGDATABASE": "railway",
        "PGUSER": "backuponly",
        "PGPASSWORD": "NEVER_LOG_THIS",
        "RCLONE_CONFIG_INSTITUTIONAL_TYPE": "drive",
        "RCLONE_CONFIG_INSTITUTIONAL_TOKEN": "PRIVATE_OAUTH_TOKEN",
        "RCLONE_CONFIG_INSTITUTIONAL_ROOT_FOLDER_ID": "FOLDER",
        "HELVOCA_C1_AGE_RECIPIENT": RECIPIENT,
    }


class NativeCronTests(unittest.TestCase):
    def setUp(self):
        self.env = settings()
        self.events = []

    def mock_dump(self, executable, path):
        self.assertEqual(executable, "pg-dump-mock")
        self.assertFalse(path.exists())
        path.write_bytes(PGDMP)
        self.events.append("pg_dump")
        return {"bytes": len(PGDMP), "sha256": hashlib.sha256(PGDMP).hexdigest()}

    def mock_command(self, args, *, failure, timeout=900):
        self.assertNotIn("NEVER_LOG_THIS", str(args))
        self.assertNotIn("PRIVATE_OAUTH_TOKEN", str(args))
        if args[:3] == ["rclone-mock", "lsf", "institutional:"]:
            self.events.append("preflight")
        elif args[0] == "age-mock":
            self.assertEqual(args[1:4], ["--encrypt", "-r", RECIPIENT])
            src = Path(args[-1])
            self.assertEqual(src.read_bytes(), PGDMP)
            self.assertTrue(src.is_file())
            Path(args[args.index("--output") + 1]).write_bytes(SEALED)
            self.events.append("encrypt")
        elif args[:3] == ["rclone-mock", "copyto", "--immutable"]:
            self.assertEqual(Path(args[3]).read_bytes(), SEALED)
            self.assertTrue(args[4].startswith("institutional:helvoca-postgres18-"))
            self.events.append("upload")
        else:
            self.fail("Unexpected command")

    def mock_readback(self, exe, remote):
        self.assertEqual(exe, "rclone-mock")
        self.assertTrue(remote.endswith(".dump.age"))
        self.events.append("readback")
        return len(SEALED), hashlib.sha256(SEALED).hexdigest()

    def test_runner_success_checks_remote_readback(self):
        with patch.object(job, "private_pg_dump", side_effect=self.mock_dump), \
             patch.object(job, "safe_run", side_effect=self.mock_command), \
             patch.object(job, "remote_cipher_digest", side_effect=self.mock_readback):
            result = job.backup_one(env=self.env, pg_dump="pg-dump-mock",
                                    age="age-mock", rclone="rclone-mock")
        self.assertEqual(self.events, ["preflight", "pg_dump", "encrypt", "upload", "readback"])
        self.assertEqual(result["status"], "C1_CRON_CIPHERTEXT_REMOTE_READBACK_PASS")
        self.assertEqual(result["cipher_sha256"], hashlib.sha256(SEALED).hexdigest())
        self.assertFalse(result["commercial_go"])
        self.assertFalse(result["remote_restore_verified_for_this_backup"])

    def test_remote_mismatch_fails_closed(self):
        with patch.object(job, "private_pg_dump", side_effect=self.mock_dump), \
             patch.object(job, "safe_run", side_effect=self.mock_command), \
             patch.object(job, "remote_cipher_digest", return_value=(len(SEALED), "0"*64)):
            with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_REMOTE_SHA256_MISMATCH"):
                job.backup_one(env=self.env, pg_dump="pg-dump-mock",
                               age="age-mock", rclone="rclone-mock")

    def test_cloud_denied_before_dump(self):
        def refuse(args, *, failure, timeout=900):
            raise job.SafeBlocker(failure)
        with patch.object(job, "safe_run", side_effect=refuse), \
             patch.object(job, "private_pg_dump") as dump:
            with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_DRIVE_NOT_READY"):
                job.backup_one(env=self.env, pg_dump="pg-dump-mock",
                               age="age-mock", rclone="rclone-mock")
            dump.assert_not_called()

    def test_encryption_failure_no_upload(self):
        def failing(args, *, failure, timeout=900):
            if args[0] == "age-mock":
                raise job.SafeBlocker("C1_CRON_AGE_ENCRYPT_FAILED")
            return self.mock_command(args, failure=failure, timeout=timeout)
        with patch.object(job, "private_pg_dump", side_effect=self.mock_dump), \
             patch.object(job, "safe_run", side_effect=failing), \
             patch.object(job, "remote_cipher_digest") as readback:
            with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_AGE_ENCRYPT_FAILED"):
                job.backup_one(env=self.env, pg_dump="pg-dump-mock",
                               age="age-mock", rclone="rclone-mock")
            readback.assert_not_called()
        self.assertNotIn("upload", self.events)

    def test_missing_secret_rejected_pre_command(self):
        for key in job.PG_ENV + job.DRIVE_ENV:
            with self.subTest(key=key):
                env = self.env.copy()
                env.pop(key)
                with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_MISSING_" + key):
                    job.preflight(env)

    def test_cloud_provider_must_be_google_drive(self):
        env = self.env.copy()
        env["RCLONE_CONFIG_INSTITUTIONAL_TYPE"] = "local"
        with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_REMOTE_NOT_GOOGLE_DRIVE"):
            job.preflight(env)

    def test_public_private_pg_host_only(self):
        for host in ("example.com", "postgres.railway.internal.attacker.com",
                     "127.0.0.1", "postgres.internal", "db railway.internal"):
            with self.subTest(host=host):
                env = self.env.copy()
                env["PGHOST"] = host
                with self.assertRaises(job.SafeBlocker):
                    job.preflight(env)

    def test_db_port_validation(self):
        for port in ("0", "65536", "wrong", "5432\n"):
            with self.subTest(port=port):
                env = self.env.copy()
                env["PGPORT"] = port
                with self.assertRaises(job.SafeBlocker):
                    job.preflight(env)

    def test_no_private_age_key_or_nonrecipient(self):
        for recipient in ("AGE-SECRET-KEY-1SENSITIVE", "", "age1invalid", "age1q;"):
            env = self.env.copy()
            env["HELVOCA_C1_AGE_RECIPIENT"] = recipient
            with self.assertRaisesRegex(job.SafeBlocker, "C1_CRON_RECIPIENT_INVALID"):
                job.preflight(env)

    def test_cli_is_inert_without_double_approval(self):
        with patch.object(job, "backup_one") as operation, \
             patch.object(job.shutil, "which") as which:
            self.assertEqual(job.main([]), 2)
            self.assertEqual(job.main(["--execute"]), 2)
            operation.assert_not_called()
            which.assert_not_called()

    def test_dump_no_password_in_command_or_logs(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "pg.dump"
            def fake_cmd(args, *, failure, timeout=900):
                self.assertEqual(args[0], "pg_dump")
                self.assertIn("--no-password", args)
                self.assertIn("--no-owner", args)
                self.assertNotIn("NEVER_LOG_THIS", str(args))
                path.write_bytes(PGDMP)
            with patch.object(job, "safe_run", side_effect=fake_cmd):
                result = job.private_pg_dump("pg_dump", path)
            self.assertEqual(result["bytes"], len(PGDMP))


if __name__ == "__main__":
    unittest.main()
