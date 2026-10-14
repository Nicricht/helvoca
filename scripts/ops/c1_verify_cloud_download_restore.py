#!/usr/bin/env python3
"""Verify ONE independently downloaded C1 cloud ciphertext without touching Railway.

The operator downloads the already-identified Google Drive .age object via an
authenticated independent download, then invokes this program locally with the
existing PRIVATE C1 export report. Only ciphertext may be downloaded. age
prompts for the passphrase directly; neither this tool nor GitHub sees it.
This proves an offline restore of bytes matching the independently observed
cloud SHA256, NOT recurring backups, password custody, full RPO/RTO, or GO.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import time

from c1_offsite_pg18_encrypted_restore import (
    AGE_HEADER, SafeBlocker, local_private_root, require, run_age,
    secure_directory, sha256_file, write_private_report,
)
from c1_replace_missing_offsite_archive import require_docker_engine
from railway_pg18_snapshot_drill import restore_to_disposable_docker, validate_dump

# Independent Google Drive authenticated download attestation recorded in #823.
CLOUD_ARCHIVE_NAME = "helvoca-postgres18-20261013T213529Z-ced3d8.dump.age"
CLOUD_ENCRYPTED_BYTES = 673001
CLOUD_SHA256 = "fdf281efa0ff75dac61e00353229d2e8f61f55c8c434a876c75d33d7449a7ce8"
ORIGINAL_PASS_STATUS = "NEW_READONLY_PG18_BACKUP_ENCRYPTED_AND_ISOLATED_RESTORE_PASSED"


def verified_inputs(downloaded: Path, private_report: Path, local_root: Path) -> dict:
    """Reject unpinned, symlink, Git, corrupted and mismatched material fail closed."""
    require(downloaded.is_absolute() and private_report.is_absolute(),
            "C1_ABSOLUTE_PATHS_REQUIRED")
    secure_directory(downloaded.parent, create=False)
    evidence_dir = secure_directory(local_root / "private-evidence", create=False)
    require(private_report.parent == evidence_dir, "C1_REPORT_OUTSIDE_PRIVATE_EVIDENCE")
    require(downloaded.name == CLOUD_ARCHIVE_NAME, "C1_CLOUD_ARCHIVE_ID_MISMATCH")
    require(downloaded.is_file() and not downloaded.is_symlink(), "C1_CLOUD_FILE_UNSAFE")
    require(private_report.is_file() and not private_report.is_symlink(), "C1_PRIVATE_REPORT_UNSAFE")
    require(downloaded.stat().st_size == CLOUD_ENCRYPTED_BYTES,
            "C1_CLOUD_ARCHIVE_SIZE_MISMATCH")
    require(sha256_file(downloaded) == CLOUD_SHA256, "C1_CLOUD_ARCHIVE_SHA_MISMATCH")
    with downloaded.open("rb") as stream:
        require(stream.read(len(AGE_HEADER)) == AGE_HEADER, "C1_CLOUD_AGE_HEADER_INVALID")
    try:
        report = json.loads(private_report.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        raise SafeBlocker("C1_PRIVATE_REPORT_INVALID") from None
    require(isinstance(report, dict) and report.get("status") == ORIGINAL_PASS_STATUS,
            "C1_LOCAL_RESTORE_ATTESTATION_MISSING")
    require(report.get("encrypted_sha256") == CLOUD_SHA256
            and type(report.get("encrypted_bytes")) is int
            and report["encrypted_bytes"] == CLOUD_ENCRYPTED_BYTES,
            "C1_LOCAL_CLOUD_CIPHERTEXT_MISMATCH")
    source_sha = report.get("source_sha256")
    require(isinstance(source_sha, str) and re.fullmatch(r"[0-9a-f]{64}", source_sha),
            "C1_PRIVATE_SOURCE_SHA_INVALID")
    require(type(report.get("source_bytes")) is int and report["source_bytes"] >= 1024,
            "C1_PRIVATE_SOURCE_SIZE_INVALID")
    return {"sha256": source_sha, "bytes": report["source_bytes"]}


def restore_downloaded(downloaded: Path, private_report: Path, local_root: Path,
                       age: str, docker: str) -> dict:
    """Check remote-pinned ciphertext, decrypt into private temp, restore PG18 offline."""
    root = secure_directory(local_root, create=False)
    expected = verified_inputs(downloaded, private_report, root)
    require_docker_engine(docker)
    start = time.monotonic()
    evidence = secure_directory(root / "private-evidence", create=False)
    with tempfile.TemporaryDirectory(prefix="helvoca-c1-cloud-", dir=evidence) as tmp:
        plaintext = Path(tmp) / "cloud-recovered.dump"
        run_age(age, ["--decrypt", "--output", str(plaintext), str(downloaded)],
                code="C1_CLOUD_DECRYPT_FAILED")
        try:
            observed = validate_dump(plaintext)
        except (OSError, ValueError):
            raise SafeBlocker("C1_CLOUD_DECRYPTED_DUMP_INVALID") from None
        require(observed == expected, "C1_CLOUD_SOURCE_DUMP_MISMATCH")
        try:
            pg = restore_to_disposable_docker(docker, plaintext)
        except (OSError, RuntimeError, ValueError):
            raise SafeBlocker("C1_CLOUD_PG18_RESTORE_FAILED") from None
    return {
        "status": "C1_CLOUD_MATCHING_CIPHERTEXT_PG18_ISOLATED_RESTORE_PASSED",
        "verified_utc": datetime.now(timezone.utc).isoformat(),
        "cloud_ciphertext_sha256": CLOUD_SHA256,
        "cloud_ciphertext_bytes": CLOUD_ENCRYPTED_BYTES,
        "decrypted_source_sha256": expected["sha256"],
        "decrypted_source_bytes": expected["bytes"],
        "isolated_restore_seconds_measured": round(time.monotonic() - start, 3),
        "isolated_pg18_public_tables": pg["public_tables"],
        "isolated_pg18_core_tables": pg["core_tables"],
        "isolated_pg18_flyway_rows": pg["flyway_history_rows"],
        "operator_independent_download_origin_verified_automatically": False,
        "decryption_secret_custody_verified": False,
        "production_rpo_measured": None,
        "production_rto_measured": None,
        "production_modified": False,
        "commercial_go": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--downloaded-ciphertext", type=Path, required=True)
    parser.add_argument("--private-report", type=Path, required=True)
    parser.add_argument("--private-root", type=Path)
    args = parser.parse_args(argv)
    try:
        age = shutil.which("age")
        docker = shutil.which("docker")
        require(bool(age), "C1_AGE_CLI_MISSING")
        require(bool(docker), "C1_DOCKER_CLI_MISSING")
        root = args.private_root if args.private_root is not None else local_private_root()
        result = restore_downloaded(args.downloaded_ciphertext, args.private_report,
                                    root, age, docker)
        report = write_private_report(result, root)
        print("C1_CLOUD_MATCHING_CIPHERTEXT_PG18_ISOLATED_RESTORE_PASSED")
        print("Private evidence: " + str(report))
        print("Remote provenance, passphrase custody and RPO/RTO still require human acceptance.")
        print("No Railway export, production write, paid service or deploy was requested.")
        return 0
    except (SafeBlocker, OSError, RuntimeError, ValueError) as exc:
        code = str(exc) if isinstance(exc, SafeBlocker) else "C1_PRIVATE_RECOVERY_FAILED"
        print("C1_BLOCKED: " + code)
        print("Production unchanged by this tool; C1 remains NO-GO.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
