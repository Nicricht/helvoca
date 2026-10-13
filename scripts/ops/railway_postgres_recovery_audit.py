#!/usr/bin/env python3
"""Non-mutating Railway Postgres recovery inventory; NOT a restore test.

Uses the caller's existing local Railway CLI login. Does not read secrets from
GitHub, Railway variables, dotenv files or arguments. Saves CLI responses only
to a PRIVATE local directory. Never uploads or prints raw CLI responses.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
POSTGRES_SERVICE = "107bb612-f25d-4c57-98a4-f494ea6b9cb4"

# These are the ONLY subcommands allowed. No restore, enable, backup create,
# connect, SSH, mutations or implied changes to live database state.
READ_ONLY_COMMANDS = (
    ("postgres", "pitr", "status"),
    ("postgres", "pitr", "backup", "list"),
)


def safe_write(directory: Path, filename: str, content: bytes) -> Path:
    """Create owner-only output; do not print possibly sensitive CLI bodies."""
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    if directory.is_symlink():
        raise ValueError("Output directory may not be a symbolic link")
    try:
        directory.chmod(0o700)
    except OSError:
        pass  # Windows ACLs are managed separately.
    path = directory / filename
    if path.is_symlink():
        raise ValueError("Output file may not be a symbolic link")
    flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, "wb") as stream:
        stream.write(content)
    if os.name != "nt":
        path.chmod(0o600)
    return path


def audit(cli: str, output_dir: Path, runner=subprocess.run) -> dict:
    results = []
    for num, command in enumerate(READ_ONLY_COMMANDS):
        cmd = [cli, *command, "--project", PROJECT,
               "--environment", ENVIRONMENT, "--service", POSTGRES_SERVICE,
               "--json"]
        try:
            response = runner(cmd, capture_output=True, check=False, timeout=60)
            data = response.stdout or b""
            error = response.stderr or b""
            code = response.returncode
        except (OSError, subprocess.TimeoutExpired):
            data, error, code = b"", b"", 127

        # CLI output might contain volume IDs, repository paths or other
        # operational details. Store only locally; never echo or commit it.
        safe_write(output_dir, f"query-{num + 1}.stdout.private", data)
        safe_write(output_dir, f"query-{num + 1}.stderr.private", error)
        results.append({
            "query": "PITR_STATUS" if num == 0 else "VOLUME_BACKUP_LIST",
            "completed_without_cli_error": code == 0,
            "response_sha256": sha256(data).hexdigest(),
            "output_present": bool(data),
        })
    result = {
        "project_id": PROJECT,
        "environment_id": ENVIRONMENT,
        "postgres_service_id": POSTGRES_SERVICE,
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "queries": results,
        # These fields MUST remain false regardless of successful list/status:
        "restoration_test_completed": False,
        "original_volume_untouched_by_this_tool": True,
        "production_deployment_authorized": False,
        "live_call_certified": False,
        "commercial_go": False,
        "conclusion": "RESTORE_PROOF_MISSING",
    }
    safe_write(output_dir, "recovery-report.private.json",
               (json.dumps(result, indent=2) + "\n").encode())
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True,
                        help="Owner-only LOCAL directory; never inside the Git repo")
    args = parser.parse_args(argv)
    cli = shutil.which("railway")
    if not cli:
        print("BLOCKED: Railway CLI is not installed in this execution environment.")
        return 2
    try:
        report = audit(cli, args.output_dir)
    except (OSError, ValueError):
        print("BLOCKED: Cannot safely write owner-only local recovery evidence.")
        return 2
    print(json.dumps({
        "read_only_queries_succeeded": all(
            q["completed_without_cli_error"] for q in report["queries"]
        ),
        "restore_test_completed": False,
        "production_deployment_authorized": False,
        "conclusion": "RESTORE_PROOF_MISSING",
        "local_evidence_directory": str(args.output_dir),
    }, indent=2))
    return 0 if all(q["completed_without_cli_error"] for q in report["queries"]) else 2


if __name__ == "__main__":
    sys.exit(main())
