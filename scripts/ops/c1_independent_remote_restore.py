#!/usr/bin/env python3
"""C1: independently restore a *browser-downloaded* Google Drive age ciphertext.

Operator downloads the verified encrypted Google Drive object in the browser to
their PC's Downloads folder. This procedure NEVER contacts Railway, uploads a
plaintext dump, reads a secret from code/env/args or modifies production.
Age prompts in the local terminal and Docker restores PG18 without networking.
All decrypted plaintext exists only temporarily in a private local directory.

LIMIT: the program verifies EXACT remote ciphertext bytes and the old local
source checksum but cannot attest how the file arrived in Downloads, assess
backups' future longevity or certify end-to-end production RPO/RTO.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

from c1_offsite_pg18_encrypted_restore import (
    AGE_HEADER, SafeBlocker, require, run_age, sha256_file, write_private_report,
)
from c1_replace_missing_offsite_archive import (
    guarded_stage, prepare_private_root, require_docker_engine,
)
from railway_pg18_snapshot_drill import validate_dump, restore_to_disposable_docker

REMOTE_FILE = "helvoca-postgres18-20261013T213529Z-ced3d8.dump.age"
REMOTE_ENCRYPTED_SHA256 = "fdf281efa0ff75dac61e00353229d2e8f61f55c8c434a876c75d33d7449a7ce8"
REMOTE_ENCRYPTED_BYTES = 673001
SOURCE_REPORT_STATUS = "NEW_READONLY_PG18_BACKUP_ENCRYPTED_AND_ISOLATED_RESTORE_PASSED"


def verify_downloaded_ciphertext(path: Path, private_root: Path) -> None:
    """Fail closed on different content, symlinks, unsafe location or missing file."""
    require(path.is_absolute(), "DOWNLOAD_PATH_NOT_ABSOLUTE")
    require(path.is_file() and not path.is_symlink(), "REMOTE_DOWNLOAD_MISSING_OR_SYMLINK")
    # The independent recovery source must NOT be the locally synchronized
    # Google Drive folder or the original local source/evidence directory.
    if sys.platform == "win32":
        require(path.drive.lower() != "g:", "USE_BROWSER_DOWNLOAD_NOT_G_DRIVE")
    require(not path.is_relative_to(private_root), "REMOTE_DOWNLOAD_INSIDE_LOCAL_PRIVATE_ROOT")
    with guarded_stage("REMOTE_DOWNLOAD_READ_FAILED"):
        require(path.stat().st_size == REMOTE_ENCRYPTED_BYTES, "REMOTE_DOWNLOAD_SIZE_MISMATCH")
        with path.open("rb") as f:
            require(f.read(len(AGE_HEADER)) == AGE_HEADER, "REMOTE_DOWNLOAD_NOT_AGE")
        require(sha256_file(path) == REMOTE_ENCRYPTED_SHA256, "REMOTE_DOWNLOAD_SHA256_MISMATCH")


def matching_source_report(private_root: Path) -> dict:
    """Match the ciphertext with its previous LOCAL verification attestation."""
    evidence = private_root / "private-evidence"
    require(evidence.is_dir() and not evidence.is_symlink(), "ORIGINAL_PRIVATE_EVIDENCE_MISSING")
    candidates = []
    with guarded_stage("ORIGINAL_PRIVATE_EVIDENCE_UNREADABLE"):
        for path in evidence.glob("helvoca-c1-offsite-*.private.json"):
            if path.is_symlink() or not path.is_file() or path.stat().st_size > 32768:
                continue
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, UnicodeError, ValueError):
                continue
            if (data.get("encrypted_sha256") == REMOTE_ENCRYPTED_SHA256
                    and data.get("encrypted_bytes") == REMOTE_ENCRYPTED_BYTES
                    and data.get("status") == SOURCE_REPORT_STATUS):
                candidates.append(data)
    require(len(candidates) == 1, "ORIGINAL_BACKUP_EVIDENCE_NOT_UNIQUE")
    expected = candidates[0]
    require(isinstance(expected.get("source_sha256"), str)
            and len(expected["source_sha256"]) == 64, "ORIGINAL_SOURCE_SHA256_MISSING")
    require(isinstance(expected.get("source_bytes"), int)
            and expected["source_bytes"] >= 1024, "ORIGINAL_SOURCE_SIZE_MISSING")
    return expected


def restore_independent_download(ciphertext: Path, *, private_root: Path,
                                 age: str, docker: str) -> dict:
    # Deliberately precheck content BEFORE asking for a passphrase.
    verify_downloaded_ciphertext(ciphertext, private_root)
    private_root = prepare_private_root(private_root)
    previous = matching_source_report(private_root)
    require_docker_engine(docker)

    start = time.monotonic()
    with guarded_stage("RECOVERY_PRIVATE_TEMP_DIRECTORY_FAILED"):
        with tempfile.TemporaryDirectory(prefix="helvoca-c1-browser-recovery-",
                                          dir=private_root / "private-evidence") as tmp:
            local_dump = Path(tmp) / "recovery.dump"
            run_age(age, ["--decrypt", "--output", str(local_dump), str(ciphertext)],
                    code="RECOVERY_AGE_DECRYPT_FAILED")
            with guarded_stage("RECOVERY_DECRYPTED_PGDMP_INVALID"):
                got = validate_dump(local_dump)
            require(got["sha256"] == previous["source_sha256"]
                    and got["bytes"] == previous["source_bytes"],
                    "RECOVERY_ORIGINAL_DUMP_SHA256_MISMATCH")
            with guarded_stage("RECOVERY_ISOLATED_PG18_RESTORE_FAILED"):
                restored = restore_to_disposable_docker(docker, local_dump)

    return {
        "status": "C1_REMOTE_DOWNLOADED_CIPHERTEXT_RESTORED_ISOLATED_PG18",
        "verified_at_utc": datetime.now(timezone.utc).isoformat(),
        "remote_encrypted_bytes": REMOTE_ENCRYPTED_BYTES,
        "remote_encrypted_sha256": REMOTE_ENCRYPTED_SHA256,
        "decrypted_source_sha256_verified": previous["source_sha256"],
        "restored_core_tables": restored["core_tables"],
        "restored_public_tables": restored["public_tables"],
        "restored_flyway_history_rows": restored["flyway_history_rows"],
        "measured_downloaded_copy_decrypt_and_local_restore_seconds": round(time.monotonic()-start, 3),
        "cloud_storage_durability_and_account_custody_verified": False,
        "operator_passphrase_custody_verified": False,
        "complete_production_rpo_verified": False,
        "complete_production_rto_verified": False,
        "production_modified": False,
        "commercial_go": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--downloaded-ciphertext", type=Path,
                        default=Path.home() / "Downloads" / REMOTE_FILE)
    parser.add_argument("--private-root", type=Path,
                        default=Path.home() / "HelvocaPrivate")
    args = parser.parse_args(argv)
    try:
        require(bool(shutil.which("age")), "AGE_CLI_MISSING")
        docker = shutil.which("docker")
        require(bool(docker), "DOCKER_CLI_MISSING")
        result = restore_independent_download(args.downloaded_ciphertext,
                                              private_root=args.private_root,
                                              age=shutil.which("age"), docker=docker)
        with guarded_stage("RECOVERY_PRIVATE_REPORT_WRITE_FAILED"):
            report = write_private_report(result, args.private_root)
        print("C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED")
        print("Local evidence path: " + str(report))
        print("No Railway access or production changes. RPO/RTO and passphrase custody remain unverified.")
        return 0
    except (SafeBlocker, OSError, RuntimeError, ValueError, subprocess.SubprocessError):
        error = sys.exc_info()[1]
        code = str(error) if isinstance(error, SafeBlocker) else "C1_PRIVATE_RECOVERY_ERROR"
        print("C1_RECOVERY_BLOCKED: " + code)
        print("Production remains unchanged; NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
