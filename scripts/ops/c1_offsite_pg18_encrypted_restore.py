#!/usr/bin/env python3
"""C1: seal and restore the ALREADY-CERTIFIED private PG18 dump, without Railway.

Uses upstream 'age' CLI passphrase encryption, not home-grown cryptography.
Prompts for the passphrase locally (never in args, variables, chat or CI).
Encrypts to a caller-chosen, independently stored directory. Decrypts into an
owner-only temporary LOCAL directory, checks pinned SHA-256, then exercises the
existing network-disabled disposable PG18 restore from Stage A.

PROVEN LIMIT: program cannot tell if an external destination is really offsite,
fully synchronized, independent of the PC, or durably retained. The operator
must confirm that separately. Exit 0 is NOT a production release authorization.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time

from railway_pg18_snapshot_drill import restore_to_disposable_docker

ARCHIVE = "helvoca-postgres18-20261013T041250Z-355b64.dump"
EXPECTED_SHA256 = "46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18"
EXPECTED_CREATED_UTC = "2026-10-13T04:12:50Z"
AGE_HEADER = b"age-encryption.org/v1\n"


class SafeBlocker(RuntimeError):
    pass


def require(ok: bool, code: str) -> None:
    if not ok:
        raise SafeBlocker(code)


def local_private_root() -> Path:
    root = os.environ.get("LOCALAPPDATA")
    require(bool(root), "LOCALAPPDATA_MISSING")
    path = Path(root) / "Helvoca"
    require(path.is_absolute(), "INVALID_LOCALAPPDATA")
    return path


def secure_directory(path: Path, *, create: bool) -> Path:
    require(path.is_absolute(), "RELATIVE_DIRECTORY_REJECTED")
    path = path.absolute()
    for ancestor in (path, *path.parents):
        require(not ancestor.is_symlink(), "SYMLINK_DIRECTORY_REJECTED")
        require(not (ancestor / ".git").exists(), "GIT_DIRECTORY_REJECTED")
    if create:
        path.mkdir(mode=0o700, parents=True, exist_ok=True)
        if os.name != "nt":
            path.chmod(0o700)
    require(path.is_dir() and not path.is_symlink(), "MISSING_OR_UNSAFE_DIRECTORY")
    return path


def validate_archive(source: Path) -> tuple[int, str]:
    require(source.is_file() and not source.is_symlink(), "ARCHIVE_MISSING_OR_SYMLINK")
    size = 0
    digest = hashlib.sha256()
    with source.open("rb") as stream:
        require(stream.read(5) == b"PGDMP", "ARCHIVE_NOT_PGDMP")
        stream.seek(0)
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(chunk)
            digest.update(chunk)
    require(size >= 1024, "ARCHIVE_TOO_SMALL")
    result = digest.hexdigest()
    require(result == EXPECTED_SHA256, "SOURCE_SHA256_MISMATCH")
    return size, result


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as inp:
        for chunk in iter(lambda: inp.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_age(age: str, args: list[str], *, code: str) -> None:
    # Interactive TTY: age asks for the passphrase itself, without Python
    # reading, logging, retaining or passing it via arguments or environment.
    try:
        result = subprocess.run([age, *args], check=False, timeout=600)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise SafeBlocker(code) from exc
    require(result.returncode == 0, code)


def write_private_report(report: dict, local_root: Path) -> Path:
    target = secure_directory(local_root / "private-evidence", create=True)
    name = ("helvoca-c1-offsite-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            + "-" + secrets.token_hex(3) + ".private.json")
    dest = target / name
    with dest.open("x", encoding="utf-8") as out:
        json.dump(report, out, indent=2, sort_keys=True)
    if os.name != "nt":
        dest.chmod(0o600)
    return dest


def seal_and_verify(source: Path, offsite: Path, age: str, docker: str,
                    *, local_root: Path) -> dict:
    source = source.absolute()
    root = secure_directory(source.parent, create=False)
    offsite = secure_directory(offsite, create=False)
    require(root != offsite, "OFFSITE_EQUALS_SOURCE_DIRECTORY")
    require(not offsite.is_relative_to(root) and not root.is_relative_to(offsite),
            "OFFSITE_NESTED_WITH_SOURCE")
    require(offsite != secure_directory(local_root / "private-evidence", create=True),
            "OFFSITE_EQUALS_EVIDENCE_DIRECTORY")
    size, checked = validate_archive(source)
    sealed = offsite / (source.name + ".age")
    require(not sealed.exists() and not sealed.is_symlink(), "OFFSITE_ARCHIVE_ALREADY_EXISTS")
    partial = offsite / (".helvoca-c1-" + secrets.token_hex(10) + ".partial")
    verified = False
    try:
        run_age(age, ["--passphrase", "--output", str(partial), str(source)],
                code="AGE_ENCRYPT_FAILED")
        require(partial.is_file() and not partial.is_symlink(),
                "AGE_ENCRYPT_OUTPUT_MISSING")
        with partial.open("rb") as stream:
            require(stream.read(len(AGE_HEADER)) == AGE_HEADER,
                    "AGE_ENCRYPT_FORMAT_INVALID")
        require(partial.stat().st_size >= size, "AGE_ENCRYPT_SIZE_INVALID")

        # 'xb' protects against overwriting a previous encrypted copy.
        try:
            with sealed.open("xb") as dst, partial.open("rb") as src:
                for block in iter(lambda: src.read(1024 * 1024), b""):
                    dst.write(block)
                dst.flush()
                os.fsync(dst.fileno())
        except BaseException:
            sealed.unlink(missing_ok=True)
            raise
        require(sha256_file(partial) == sha256_file(sealed),
                "OFFSITE_CIPHER_COPY_MISMATCH")

        work = secure_directory(local_root / "private-evidence", create=True)
        start = time.monotonic()
        with tempfile.TemporaryDirectory(prefix="helvoca-c1-", dir=work) as tmp:
            clear = Path(tmp) / "restored-from-offsite.dump"
            run_age(age, ["--decrypt", "--output", str(clear), str(sealed)],
                    code="AGE_DECRYPT_FAILED")
            # Digest and format must be identical to the proven Stage A archive.
            validate_archive(clear)
            result = restore_to_disposable_docker(docker, clear)
        recovery_seconds = round(time.monotonic() - start, 3)
        verified = True
        backup_created = datetime.fromisoformat(EXPECTED_CREATED_UTC.replace("Z", "+00:00"))
        return {
            "status": "OFFSITE_CIPHERTEXT_DECRYPT_AND_ISOLATED_PG18_RESTORE_PASSED",
            "source_backup_created_utc": EXPECTED_CREATED_UTC,
            "verification_utc": datetime.now(timezone.utc).isoformat(),
            "backup_age_hours_at_verification":
                round((datetime.now(timezone.utc) - backup_created).total_seconds() / 3600, 2),
            "source_bytes": size,
            "source_sha256": checked,
            "encrypted_sha256": sha256_file(sealed),
            "encrypted_bytes": sealed.stat().st_size,
            "isolated_restore_seconds_measured": recovery_seconds,
            "isolated_pg18_public_tables": result["public_tables"],
            "isolated_pg18_core_tables": result["core_tables"],
            "isolated_pg18_flyway_rows": result["flyway_history_rows"],
            "independent_storage_verified": False,
            "remote_sync_or_offsite_durability_verified": False,
            "operator_has_separately_stored_decryption_passphrase_verified": False,
            "measured_production_rpo": None,
            "measured_production_rto": None,
            "production_modified": False,
            "commercial_go": False,
        }
    finally:
        partial.unlink(missing_ok=True)
        # An encrypted copy from an unsuccessful drill can be retained for
        # retry, but must never be declared restore-certified.
        if not verified:
            print("C1_BLOCKED: encrypted copy must not be considered recovery-certified.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offsite-dir", type=Path, required=True,
                        help="Pre-existing independent destination (external drive or synced private storage)")
    args = parser.parse_args(argv)
    try:
        private_root = local_private_root()
        source = secure_directory(private_root / "private-backups", create=False) / ARCHIVE
        age = shutil.which("age")
        docker = shutil.which("docker")
        require(bool(age), "AGE_CLI_MISSING")
        require(bool(docker), "DOCKER_CLI_MISSING")
        report = seal_and_verify(source, args.offsite_dir, age, docker,
                                 local_root=private_root)
        output = write_private_report(report, private_root)
        print("C1_ENCRYPTED_COPY_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED")
        print("The local passphrase must be stored separately from the encrypted archive.")
        print("IMPORTANT: Offsite independence, sync durability, production RPO/RTO NOT certified.")
        print("Private local report: " + str(output))
        print("No changes to Railway, GitHub main, Twilio or any production database.")
        return 0
    except (SafeBlocker, OSError, ValueError, subprocess.SubprocessError) as exc:
        code = str(exc) if isinstance(exc, SafeBlocker) else "PRIVATE_IO_OR_VERIFY_FAILED"
        print("C1_BLOCKED: " + code)
        print("Production remains unchanged; NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
