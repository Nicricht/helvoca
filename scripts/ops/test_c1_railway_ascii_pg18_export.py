#!/usr/bin/env python3
"""Synthetic transport regressions. NO real Railway, SSH, PG data or secrets."""
import base64
import io
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

import c1_railway_ascii_pg18_export as export


DUMP = b"PGDMP" + b"synthetic-no-customer-data" * 200


class FakeSSHProcess:
    def __init__(self, stdout: bytes, code: int = 0):
        self.stdout = io.BytesIO(stdout)
        self._exit = code
        self._returncode = None
        self.killed = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return None

    def wait(self, timeout=None):
        self._returncode = self._exit
        return self._exit

    def poll(self):
        return self._returncode

    def kill(self):
        self.killed = True
        self._returncode = -9


class AsciiTransportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "test-private.dump"
        self.commands = []

    def emulate(self, *, data=DUMP, include_begin=True, include_end=True,
                payload=None, remote_exit=0, prefix=b"Using SSH key from file ...\r\n"):
        def factory(cmd, **kwargs):
            self.commands.append(cmd)
            self.assertEqual(cmd[0], "railway-test")
            self.assertEqual(cmd[-3:-1], ["bash", "-c"])
            self.assertIn("set -o pipefail", cmd[-1])
            self.assertIn("pg_dump --format=custom --no-owner --no-acl", cmd[-1])
            self.assertIn('| base64;', cmd[-1])
            self.assertNotIn("real_password", cmd[-1])
            start = re.search(r"HELVOCA_C1_BEGIN_[0-9a-f]{32}", cmd[-1]).group().encode()
            end = re.search(r"HELVOCA_C1_END_[0-9a-f]{32}", cmd[-1]).group().encode()
            parts = [prefix]
            if include_begin:
                parts.append(start + b"\r\n")
            parts.append(base64.encodebytes(data) if payload is None else payload)
            if include_end:
                parts.append(b"\r\n" + end + b"\r\nConnection to ssh.railway.com closed.\r\n")
            return FakeSSHProcess(b"".join(parts), code=remote_exit)
        return factory

    def test_success_transfers_bytes_exactly_ignoring_ssh_banners(self):
        with patch.object(export.subprocess, "Popen", side_effect=self.emulate()):
            result = export.dump_from_railway_ascii("railway-test", self.path)
        self.assertEqual(result["bytes"], len(DUMP))
        self.assertEqual(self.path.read_bytes(), DUMP)
        self.assertEqual(len(self.commands), 1)

    def test_streams_multiple_chunks_without_corrupting_binary_dump(self):
        large = b"PGDMP" + bytes(range(256)) * 5000
        with patch.object(export.subprocess, "Popen", side_effect=self.emulate(data=large)):
            result = export.dump_from_railway_ascii("railway-test", self.path)
        self.assertEqual(result["bytes"], len(large))
        self.assertEqual(self.path.read_bytes(), large)

    def test_missing_start_refuses_to_create_backup(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(include_begin=False)):
            with self.assertRaises(export.ExportBlocked):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_missing_end_refuses_to_create_backup(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(include_end=False)):
            with self.assertRaisesRegex(export.ExportBlocked, "FRAME_INCOMPLETE"):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_remote_error_never_certifies_even_if_stream_looks_valid(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(remote_exit=9)):
            with self.assertRaisesRegex(export.ExportBlocked, "REMOTE_EXIT_"):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_rejects_invalid_base64_and_deletes_partial(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(payload=b"******\n")):
            with self.assertRaisesRegex(export.ExportBlocked, "INVALID_BASE64"):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_rejects_incorrect_pg_dump_magic_and_deletes_partial(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(data=b"NOTPG" + DUMP[5:])):
            with self.assertRaisesRegex(export.ExportBlocked, "PGDMP_VALIDATION_FAILED"):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_rejects_oversized_untrusted_stream_line(self):
        with patch.object(export.subprocess, "Popen",
                          side_effect=self.emulate(payload=b"A"*4200+b"\n")):
            with self.assertRaisesRegex(export.ExportBlocked, "LINE_TOO_LONG"):
                export.dump_from_railway_ascii("railway-test", self.path)
        self.assertFalse(self.path.exists())

    def test_never_overwrites_existing_local_private_backup(self):
        self.path.write_bytes(b"existing-private-backup")
        with patch.object(export.subprocess, "Popen") as client:
            with self.assertRaises(FileExistsError):
                export.dump_from_railway_ascii("railway-test", self.path)
            client.assert_not_called()
        self.assertEqual(self.path.read_bytes(), b"existing-private-backup")


if __name__ == "__main__":
    unittest.main()
