#!/usr/bin/env python3
"""C1: replace a missing historical dump with ONE new read-only Railway export.

Execute locally with authenticated Railway CLI, Docker Desktop, age and a
pre-existing cloud-synced destination. The raw dump NEVER leaves private local
storage; only age ciphertext is written to the offsite directory. A disposable,
network-disabled PostgreSQL 18 restore validates the ciphertext's contents.
The program cannot certify Google Drive remote synchronization or production
RPO/RTO; neither success nor a local test authorizes a production deployment.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import time

from c1_offsite_pg18_encrypted_restore import (
    AGE_HEADER, SafeBlocker, local_private_root, require, run_age,
    secure_directory, sha256_file, write_private_report,
)
from c1_railway_ascii_pg18_export import ExportBlocked, dump_from_railway_ascii as dump_from_railway
from railway_pg18_snapshot_drill import restore_to_disposable_docker, validate_dump


@contextmanager
def guarded_stage(code: str):
    """Expose only bounded, actionable step codes, never raw provider errors."""
    try:
        yield
    except SafeBlocker:
        raise
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        if code == "RAILWAY_READONLY_EXPORT_FAILED" and isinstance(exc, ExportBlocked):
            raise SafeBlocker(str(exc)) from None
        if code == "RAILWAY_READONLY_EXPORT_FAILED":
            # The existing Railway exporter already replaces private stderr
            # with a limited safe classification. Forward only known codes.
            for category in ("SSH_KEY_OR_AUTH", "PG_DUMP_OR_DATABASE_AUTH",
                             "REMOTE_COMMAND_UNAVAILABLE", "SSH_CONNECTIVITY"):
                if "category=" + category in str(exc):
                    raise SafeBlocker("RAILWAY_READONLY_EXPORT_" + category) from None
        raise SafeBlocker(code) from None


def require_docker_engine(docker: str) -> None:
    """Fail BEFORE a new private export when Docker Desktop is not running."""
    try:
        result = subprocess.run([docker, "info", "--format", "{{.ServerVersion}}"],
                                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=20, check=False)
    except (OSError, subprocess.TimeoutExpired, subprocess.SubprocessError):
        raise SafeBlocker("DOCKER_ENGINE_NOT_READY") from None
    require(result.returncode == 0, "DOCKER_ENGINE_NOT_READY")


def backup_encrypt_and_verify(offsite_dir: Path, railway: str, age: str, docker: str,
                              *, local_root: Path) -> dict:
    """No Railway writes, no plaintext in offsite, no false offsite durability claim."""
    with guarded_stage("LOCAL_BACKUP_DIRECTORY_UNAVAILABLE"):
        source_dir = secure_directory(local_root / "private-backups", create=True)
    with guarded_stage("OFFSITE_DRIVE_DIRECTORY_UNAVAILABLE"):
        require(offsite_dir.is_dir(), "OFFSITE_DRIVE_DIRECTORY_UNAVAILABLE")
        offsite_dir = secure_directory(offsite_dir, create=False)
    require(source_dir != offsite_dir, "OFFSITE_EQUALS_SOURCE_DIRECTORY")
    require(not offsite_dir.is_relative_to(source_dir)
            and not source_dir.is_relative_to(offsite_dir), "OFFSITE_NESTED_WITH_SOURCE")
    require(offsite_dir != secure_directory(local_root / "private-evidence", create=True),
            "OFFSITE_EQUALS_EVIDENCE_DIRECTORY")

    stamp = datetime.now(timezone.utc)
    name = "helvoca-postgres18-" + stamp.strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3) + ".dump"
    local_dump = source_dir / name
    # Remote pg_dump is sent as framed ASCII base64 over SSH and decoded
    # incrementally into LOCAL private storage. This avoids binary/PTY corruption.
    with guarded_stage("RAILWAY_READONLY_EXPORT_FAILED"):
        metadata = dump_from_railway(railway, local_dump)
    require(local_dump.is_file() and not local_dump.is_symlink(), "NEW_DUMP_UNSAFE")
    with guarded_stage("LOCAL_DUMP_VERIFY_FAILED"):
        actual = validate_dump(local_dump)
    require(actual["sha256"] == metadata["sha256"] and actual["bytes"] == metadata["bytes"],
            "NEW_DUMP_CHANGED_AFTER_CAPTURE")

    ciphertext = offsite_dir / (name + ".age")
    require(not ciphertext.exists() and not ciphertext.is_symlink(), "OFFSITE_ARCHIVE_ALREADY_EXISTS")
    partial = offsite_dir / (".helvoca-c1-" + secrets.token_hex(10) + ".partial")
    copy_verified = False
    try:
        # age obtains and confirms the passphrase in its own interactive TTY.
        # No passphrase through a Python variable, CLI argument, CI, or logs.
        run_age(age, ["--passphrase", "--output", str(partial), str(local_dump)],
                code="AGE_ENCRYPT_FAILED")
        require(partial.is_file() and not partial.is_symlink(), "AGE_ENCRYPT_OUTPUT_MISSING")
        with guarded_stage("OFFSITE_ENCRYPTED_TEMP_READ_FAILED"):
            with partial.open("rb") as f:
                require(f.read(len(AGE_HEADER)) == AGE_HEADER, "AGE_ENCRYPT_FORMAT_INVALID")
            require(partial.stat().st_size >= actual["bytes"], "AGE_ENCRYPT_SIZE_INVALID")
        created = False
        try:
            with ciphertext.open("xb") as dest:
                created = True
                with partial.open("rb") as src:
                    for block in iter(lambda: src.read(1024 * 1024), b""):
                        dest.write(block)
                dest.flush()
                os.fsync(dest.fileno())
        except BaseException as exc:
            if created:
                ciphertext.unlink(missing_ok=True)
            if isinstance(exc, (OSError, RuntimeError, ValueError)) and not isinstance(exc, SafeBlocker):
                raise SafeBlocker("OFFSITE_ENCRYPTED_WRITE_FAILED") from None
            raise
        with guarded_stage("OFFSITE_ENCRYPTED_VERIFY_FAILED"):
            require(sha256_file(ciphertext) == sha256_file(partial), "OFFSITE_CIPHER_COPY_MISMATCH")
        copy_verified = True

        t0 = time.monotonic()
        with guarded_stage("LOCAL_ISOLATED_RESTORE_DIRECTORY_FAILED"):
            evidence_dir = secure_directory(local_root / "private-evidence", create=True)
        with guarded_stage("LOCAL_RECOVERY_IO_FAILED"):
            with tempfile.TemporaryDirectory(prefix="helvoca-c1-", dir=evidence_dir) as tmp:
                clear = Path(tmp) / "restored-from-ciphertext.dump"
                run_age(age, ["--decrypt", "--output", str(clear), str(ciphertext)],
                        code="AGE_DECRYPT_FAILED")
                with guarded_stage("DECRYPTED_DUMP_FORMAT_FAILED"):
                    recovered = validate_dump(clear)
                require(recovered == actual, "RESTORED_DUMP_CHECKSUM_MISMATCH")
                with guarded_stage("LOCAL_PG18_ISOLATED_RESTORE_FAILED"):
                    checked = restore_to_disposable_docker(docker, clear)
        duration_seconds = round(time.monotonic() - t0, 3)
        return {
            "status": "NEW_READONLY_PG18_BACKUP_ENCRYPTED_AND_ISOLATED_RESTORE_PASSED",
            "backup_created_utc": stamp.isoformat(),
            "verification_utc": datetime.now(timezone.utc).isoformat(),
            "source_bytes": actual["bytes"],
            "source_sha256": actual["sha256"],
            "encrypted_bytes": ciphertext.stat().st_size,
            "encrypted_sha256": sha256_file(ciphertext),
            "isolated_restore_seconds_measured": duration_seconds,
            "isolated_pg18_public_tables": checked["public_tables"],
            "isolated_pg18_core_tables": checked["core_tables"],
            "isolated_pg18_flyway_rows": checked["flyway_history_rows"],
            "remote_sync_or_offsite_durability_verified": False,
            "independent_storage_verified": False,
            "operator_has_separately_stored_decryption_passphrase_verified": False,
            "measured_production_rpo": None,
            "measured_production_rto": None,
            "production_modified": False,
            "commercial_go": False,
        }
    finally:
        partial.unlink(missing_ok=True)
        if not copy_verified:
            print("C1_BLOCKED: no certified encrypted offsite copy created.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offsite-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        railway, age, docker = (shutil.which(name) for name in ("railway", "age", "docker"))
        require(bool(railway), "RAILWAY_CLI_MISSING")
        require(bool(age), "AGE_CLI_MISSING")
        require(bool(docker), "DOCKER_CLI_MISSING")
        with guarded_stage("OFFSITE_DRIVE_DIRECTORY_UNAVAILABLE"):
            require(args.offsite_dir.is_dir(), "OFFSITE_DRIVE_DIRECTORY_UNAVAILABLE")
        require_docker_engine(docker)
        result = backup_encrypt_and_verify(args.offsite_dir, railway, age, docker,
                                           local_root=local_private_root())
        with guarded_stage("LOCAL_PRIVATE_REPORT_WRITE_FAILED"):
            report = write_private_report(result, local_private_root())
        print("C1_NEW_BACKUP_ENCRYPTED_AND_OFFLINE_PG18_RESTORE_VERIFIED")
        print("Private local evidence: " + str(report))
        print("Cloud upload, remote retrieval, separated passphrase custody and RPO/RTO are NOT certified.")
        print("No production database mutation or migration has been requested.")
        return 0
    except (SafeBlocker, OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        code = str(exc) if isinstance(exc, SafeBlocker) else "C1_PRIVATE_OPERATION_FAILED"
        print("C1_BLOCKED: " + code)
        print("Production remains unchanged by this script; NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
