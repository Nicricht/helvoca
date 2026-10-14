#!/usr/bin/env python3
"""C1 unattended backup to user-approved institutional Drive via rclone.

OFF by default. A real run requires --execute and an explicit enable flag.
Requires:
  * an authenticated Railway CLI that can run the existing read-only SSH export
  * age with a PUBLIC age1... X25519 recipient; private recovery key stored OFF-job
  * rclone with a private OAuth-backed Google Drive remote configured on the job host
  * non-cloud, non-Git private local workspace
This is a CLI runner, NOT a deployed cron. It does not modify the database,
delete remote files, invoke docker, install software or expose private bytes.
Upload is acknowledged only after an independent rclone cat SHA256 readback.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time

from c1_offsite_pg18_encrypted_restore import AGE_HEADER, SafeBlocker, require, sha256_file, write_private_report
from c1_replace_missing_offsite_archive import prepare_private_root
from c1_railway_ascii_pg18_export import dump_from_railway_ascii
from railway_pg18_snapshot_drill import validate_dump

ENABLE_ENV = "HELVOCA_C1_ALLOW_EXECUTION"
ENABLE_VALUE = "read_only_encrypted_backup_approved"
REMOTE_RE = re.compile(r"^[A-Za-z0-9_-]+:[A-Za-z0-9_/-]+$")
RECIPIENT_RE = re.compile(r"^age1[023456789acdefghjklmnpqrstuvwxyz]{58}$")
TIMEOUT_SECONDS = 900


def safe_run(argv: list[str], *, failure: str, timeout: int = TIMEOUT_SECONDS) -> None:
    """Never log provider command output, stderr, credentials or paths."""
    try:
        result = subprocess.run(argv, stdin=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired, subprocess.SubprocessError):
        raise SafeBlocker(failure) from None
    require(result.returncode == 0, failure)


def remote_cipher_digest(rclone: str, remote_object: str) -> tuple[int, str]:
    """Read full ciphertext BACK from provider, streaming without local clear data."""
    cmd = [rclone, "cat", remote_object]
    size = 0
    digest = hashlib.sha256()
    timed_out = threading.Event()
    try:
        with subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL) as proc:
            def terminate() -> None:
                timed_out.set()
                try:
                    proc.kill()
                except OSError:
                    pass
            timer = threading.Timer(TIMEOUT_SECONDS, terminate)
            timer.daemon = True
            timer.start()
            try:
                assert proc.stdout is not None
                while True:
                    block = proc.stdout.read(1024 * 1024)
                    if not block:
                        break
                    size += len(block)
                    digest.update(block)
                code = proc.wait(timeout=20)
            finally:
                timer.cancel()
                if proc.poll() is None:
                    proc.kill()
                    proc.wait()
    except (OSError, subprocess.SubprocessError):
        raise SafeBlocker("C1_REMOTE_READBACK_FAILED") from None
    require(not timed_out.is_set(), "C1_REMOTE_READBACK_TIMEOUT")
    require(code == 0, "C1_REMOTE_READBACK_FAILED")
    return size, digest.hexdigest()


def require_approval(*, execute: bool, env: dict[str, str]) -> None:
    require(execute and env.get(ENABLE_ENV) == ENABLE_VALUE, "C1_UNATTENDED_NOT_ENABLED")


def run_backup(*, private_root: Path, remote: str, recipient: str, railway: str,
               age: str, rclone: str) -> dict:
    require(bool(REMOTE_RE.fullmatch(remote)) and ".." not in remote,
            "C1_REMOTE_NAME_UNSAFE")
    require(bool(RECIPIENT_RE.fullmatch(recipient)), "C1_AGE_RECIPIENT_INVALID")
    # Refuse local mirror and all known private root nesting before any export.
    require(not remote.startswith("local:"), "C1_REMOTE_NOT_CLOUD")
    private_root = prepare_private_root(private_root)
    safe_run([rclone, "lsf", remote], failure="C1_INSTITUTIONAL_DRIVE_NOT_READY", timeout=45)

    stamp = datetime.now(timezone.utc)
    name = ("helvoca-postgres18-" + stamp.strftime("%Y%m%dT%H%M%SZ") + "-"
            + secrets.token_hex(6) + ".dump")
    remote_object = remote.rstrip("/") + "/" + name + ".age"
    began = time.monotonic()

    # Plaintext and ciphertext are transient and restricted to a PRIVATE local folder.
    with tempfile.TemporaryDirectory(prefix="c1-unattended-", dir=private_root / "private-backups") as tmp:
        clear = Path(tmp) / name
        cipher = Path(tmp) / (name + ".age")
        exported = dump_from_railway_ascii(railway, clear)
        require(exported == validate_dump(clear), "C1_SOURCE_CHANGED_AFTER_EXPORT")
        safe_run([age, "--encrypt", "-r", recipient, "--output", str(cipher), str(clear)],
                 failure="C1_AGE_ENCRYPT_FAILED")
        require(cipher.is_file() and not cipher.is_symlink(), "C1_CIPHER_MISSING")
        with cipher.open("rb") as fh:
            require(fh.read(len(AGE_HEADER)) == AGE_HEADER, "C1_CIPHER_INVALID")
        local_size = cipher.stat().st_size
        local_digest = sha256_file(cipher)
        # --immutable never overwrites a different existing remote object.
        safe_run([rclone, "copyto", "--immutable", str(cipher), remote_object],
                 failure="C1_REMOTE_UPLOAD_FAILED")
        fetched_size, fetched_digest = remote_cipher_digest(rclone, remote_object)
        require((fetched_size, fetched_digest) == (local_size, local_digest),
                "C1_REMOTE_CIPHER_SHA256_MISMATCH")
        # On any failure, never delete an existing remote copy or report success.
        return {
            "status": "C1_SCHEDULED_RUN_REMOTE_CIPHER_READBACK_PASSED",
            "backup_created_utc": stamp.isoformat(),
            "remote_object_name": name + ".age",
            "remote_sha256": fetched_digest,
            "remote_bytes": fetched_size,
            "source_sha256": exported["sha256"],
            "source_bytes": exported["bytes"],
            "run_duration_seconds": round(time.monotonic() - began, 3),
            "cloud_account_institutional_chosen_by_operator": True,
            "rpo_rto_sla_certified": False,
            "separated_private_key_custody_certified": False,
            "recovery_from_this_new_cipher_certified": False,
            "production_modified": False,
            "commercial_go": False,
        }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--remote", required=True, help="rclone remote:folder (never public link)")
    parser.add_argument("--recipient", required=True, help="PUBLIC age1... encryption recipient")
    parser.add_argument("--private-root", type=Path, default=Path.home() / "HelvocaPrivate")
    parser.add_argument("--execute", action="store_true", help="perform one REAL read-only backup")
    args = parser.parse_args(argv)
    try:
        # No external interaction before both explicit gates have passed.
        require_approval(execute=args.execute, env=os.environ)
        binaries = {name: shutil.which(name) for name in ("railway", "age", "rclone")}
        for name in ("railway", "age", "rclone"):
            require(bool(binaries[name]), "C1_TOOL_MISSING_" + name.upper())
        result = run_backup(private_root=args.private_root, remote=args.remote,
                            recipient=args.recipient, railway=binaries["railway"],
                            age=binaries["age"], rclone=binaries["rclone"])
        report = write_private_report(result, args.private_root)
        print("C1_SCHEDULED_CIPHER_REMOTE_READBACK_VERIFIED")
        print("Private local report: " + str(report))
        print("No restore or RPO/RTO certification in this run; institutional account risk remains.")
        return 0
    except (SafeBlocker, OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        code = str(exc) if isinstance(exc, SafeBlocker) else "C1_SCHEDULED_PRIVATE_OPERATION_FAILED"
        print("C1_SCHEDULED_BLOCKED: " + code)
        print("No production mutations or remote deletions requested; commercial NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
