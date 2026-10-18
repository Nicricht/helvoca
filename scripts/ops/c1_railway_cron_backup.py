#!/usr/bin/env python3
"""C1: safe Railway-native Cron runner for ONE read-only pg_dump to institutional Drive.

Preparation only; NO scheduler, secrets, deployment or database writes are
configured by committing this script. Invoking it needs BOTH --execute AND
HELVOCA_C1_ALLOW_EXECUTION=read_only_encrypted_backup_approved.

Runtime: PostgreSQL 18 pg_dump, age, rclone, Python 3.11+. PostgreSQL
connection through PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD in the service
environment. No Railway CLI, no public TCP proxy, no passphrases or private
age keys in the job. Encryption recipient is PUBLIC and provided by env.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time

from c1_offsite_pg18_encrypted_restore import AGE_HEADER, SafeBlocker, require, sha256_file
from c1_unattended_institutional_backup import (
    RECIPIENT_RE, ENABLE_ENV, ENABLE_VALUE, require_approval, safe_run,
    remote_cipher_digest,
)
from railway_pg18_snapshot_drill import validate_dump

# Only the already-authorized institutional rclone remote. It is configured
# outside Git using RCLONE_CONFIG_INSTITUTIONAL_* variables on the Cron service.
REMOTE_ROOT = "institutional:"
MIN_FREE_BYTES = 100 * 1024 * 1024
PG_ENV = ("PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD")
DRIVE_ENV = ("RCLONE_CONFIG_INSTITUTIONAL_TYPE", "RCLONE_CONFIG_INSTITUTIONAL_TOKEN",
             "RCLONE_CONFIG_INSTITUTIONAL_ROOT_FOLDER_ID")


def preflight(env: dict[str, str]) -> str:
    """Reject missing secrets, misdirected remote, absent recipient BEFORE export."""
    for key in (*PG_ENV, *DRIVE_ENV):
        require(bool(env.get(key, "").strip()), "C1_CRON_MISSING_" + key)
    require(env["RCLONE_CONFIG_INSTITUTIONAL_TYPE"] == "drive",
            "C1_CRON_REMOTE_NOT_GOOGLE_DRIVE")
    require(bool(re.fullmatch(r"[0-9]{1,5}", env["PGPORT"]))
            and 1 <= int(env["PGPORT"]) <= 65535, "C1_CRON_PGPORT_INVALID")
    host = env["PGHOST"].strip()
    require("railway.internal" in host and host.endswith(".railway.internal"),
            "C1_CRON_PGHOST_NOT_PRIVATE")
    require(not any(c.isspace() for c in host), "C1_CRON_PGHOST_INVALID")
    recipient = env.get("HELVOCA_C1_AGE_RECIPIENT", "")
    require(bool(RECIPIENT_RE.fullmatch(recipient)), "C1_CRON_RECIPIENT_INVALID")
    return recipient


def private_pg_dump(pg_dump: str, dst: Path) -> dict:
    """Direct local VPC read-only Postgres 18 pg_dump; no password in argv/logs."""
    require(dst.is_absolute() and not dst.exists(), "C1_CRON_UNSAFE_DUMP_PATH")
    safe_run([pg_dump, "--format=custom", "--no-owner", "--no-acl",
              "--lock-wait-timeout=30000", "--no-password", "--file", str(dst)],
             failure="C1_CRON_PG_DUMP_FAILED", timeout=600)
    require(dst.is_file() and not dst.is_symlink(), "C1_CRON_PG_DUMP_MISSING")
    try:
        return validate_dump(dst)
    except (OSError, ValueError):
        raise SafeBlocker("C1_CRON_PGDMP_INVALID") from None


def backup_one(*, env: dict[str, str], pg_dump: str, age: str, rclone: str) -> dict:
    recipient = preflight(env)
    # Verify cloud authentication and accessible private destination BEFORE DB.
    safe_run([rclone, "lsf", REMOTE_ROOT], failure="C1_CRON_DRIVE_NOT_READY", timeout=45)
    require(shutil.disk_usage(tempfile.gettempdir()).free >= MIN_FREE_BYTES,
            "C1_CRON_DISK_SPACE_LOW")

    stamp = datetime.now(timezone.utc)
    name = ("helvoca-postgres18-" + stamp.strftime("%Y%m%dT%H%M%SZ")
            + "-" + secrets.token_hex(6) + ".dump")
    object_path = REMOTE_ROOT + name + ".age"
    started = time.monotonic()
    # Temp directory is only in the ephemeral Railway worker, not synchronized.
    with tempfile.TemporaryDirectory(prefix="helvoca-c1-") as work:
        root = Path(work)
        if os.name != "nt":
            root.chmod(0o700)
        clear = root / name
        sealed = root / (name + ".age")
        source = private_pg_dump(pg_dump, clear)
        safe_run([age, "--encrypt", "-r", recipient, "--output", str(sealed), str(clear)],
                 failure="C1_CRON_AGE_ENCRYPT_FAILED")
        require(sealed.is_file() and not sealed.is_symlink(), "C1_CRON_CIPHER_MISSING")
        with sealed.open("rb") as inp:
            require(inp.read(len(AGE_HEADER)) == AGE_HEADER, "C1_CRON_CIPHER_NOT_AGE")
        size, local_hash = sealed.stat().st_size, sha256_file(sealed)
        require(size >= source["bytes"], "C1_CRON_CIPHER_TOO_SMALL")
        safe_run([rclone, "copyto", "--immutable", str(sealed), object_path],
                 failure="C1_CRON_DRIVE_UPLOAD_FAILED")
        remote_size, remote_hash = remote_cipher_digest(rclone, object_path)
        require((size, local_hash) == (remote_size, remote_hash),
                "C1_CRON_REMOTE_SHA256_MISMATCH")
    return {
        "status": "C1_CRON_CIPHERTEXT_REMOTE_READBACK_PASS",
        "backup_created_utc": stamp.isoformat(),
        "remote_cipher_filename": name + ".age",
        "cipher_sha256": local_hash,
        "cipher_bytes": size,
        "plain_sha256": source["sha256"],
        "plain_bytes": source["bytes"],
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "db_export_read_only": True,
        "key_custody_verified": False,
        "remote_restore_verified_for_this_backup": False,
        "rpo_rto_certified": False,
        "commercial_go": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args(argv)
    try:
        require_approval(execute=args.execute, env=os.environ)
        preflight(os.environ)
        binary = {name: shutil.which(name) for name in ("pg_dump", "age", "rclone")}
        for name in ("pg_dump", "age", "rclone"):
            require(bool(binary[name]), "C1_CRON_TOOL_MISSING_" + name.upper())
        outcome = backup_one(env=os.environ, pg_dump=binary["pg_dump"],
                             age=binary["age"], rclone=binary["rclone"])
        # Never emit filenames, tenant PII, OAuth, DB credentials or raw dump.
        print("C1_CRON_ENCRYPTED_DRIVE_SHA256_READBACK_VERIFIED")
        print("Fresh dump encrypted and verified; operational RPO/RTO and restore still pending.")
        return 0
    except (SafeBlocker, OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        code = str(exc) if isinstance(exc, SafeBlocker) else "C1_CRON_UNEXPECTED_PRIVATE_FAILURE"
        print("C1_CRON_BLOCKED: " + code)
        print("No database writes or remote deletions requested; backup not certified.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
