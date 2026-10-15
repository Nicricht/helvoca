#!/usr/bin/env python3
"""No-provider, no-network regression for a retired same-server restore drill."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("postgres-restore-drill.sh")


class LegacyRestoreFailClosedTest(unittest.TestCase):
    def verify_rejected(self, *, credentials: bool, old_approval: bool = False) -> None:
        with tempfile.TemporaryDirectory(prefix="c1-synthetic-") as directory:
            root = Path(directory)
            tools = root / "bin"
            tools.mkdir()
            marker = root / "forbidden-invocation"
            for name in ("pg_dump", "createdb", "pg_restore", "psql", "dropdb"):
                fake = tools / name
                fake.write_text(
                    "#!/bin/sh\n"
                    'printf "called\\n" >> "$HELVOCA_TEST_MARKER"\n'
                    "exit 99\n", encoding="utf-8")
                fake.chmod(0o755)
            env = os.environ.copy()
            for key in ("PGHOST", "PGPORT", "PGUSER", "PGPASSWORD",
                        "PGDATABASE", "RESTORE_DB", "BACKUP_FILE",
                        "HELVOCA_C1_ALLOW_EXECUTION"):
                env.pop(key, None)
            env.update({
                "PATH": str(tools) + os.pathsep + env.get("PATH", ""),
                "HELVOCA_TEST_MARKER": str(marker),
            })
            if credentials:
                env.update({
                    "PGHOST": "synthetic-production.railway.internal",
                    "PGPORT": "5432",
                    "PGUSER": "test",
                    "PGPASSWORD": "SYNTHETIC_SECRET_DO_NOT_PRINT",
                    "PGDATABASE": "production",
                    "RESTORE_DB": "helvoca_restore_drill_20261015000000",
                    "BACKUP_FILE": str(root / "secret.dump"),
                })
            if old_approval:
                env["HELVOCA_C1_ALLOW_EXECUTION"] = "read_only_encrypted_backup_approved"
            result = subprocess.run(["bash", str(SCRIPT)], env=env,
                                    capture_output=True, text=True, timeout=10,
                                    check=False)
            self.assertEqual(result.returncode, 2)
            self.assertIn("C1_LEGACY_SAME_SERVER_RESTORE_BLOCKED", result.stderr)
            self.assertEqual(result.stdout, "")
            self.assertNotIn("SYNTHETIC_SECRET_DO_NOT_PRINT", result.stderr)
            self.assertFalse(marker.exists(), "no DB CLI may be started")
            self.assertFalse((root / "secret.dump").exists())

    def test_production_like_connection_is_never_used(self) -> None:
        self.verify_rejected(credentials=True)

    def test_missing_connection_is_rejected_before_any_client(self) -> None:
        self.verify_rejected(credentials=False)

    def test_obsolete_opt_in_cannot_bypass_the_block(self) -> None:
        self.verify_rejected(credentials=True, old_approval=True)


if __name__ == "__main__":
    unittest.main()
