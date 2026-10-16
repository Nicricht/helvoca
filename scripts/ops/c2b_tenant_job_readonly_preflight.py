#!/usr/bin/env python3
"""C2-B read-only aggregate inventory of tenant pilot state and durable jobs.

No customer identifiers, payloads, credentials, mutations, live provider traffic,
or GO release result. This cannot prove terminal cessation of in-flight effects.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
POSTGRES_SERVICE = "107bb612-f25d-4c57-98a4-f494ea6b9cb4"
PREFIX = "C2B_TENANT_JOB_COUNTS_V1:"
COUNTS = (
    "tenants_total", "tenants_unmanaged", "pilots_total", "pilots_running",
    "pilots_paused", "jobs_pending", "jobs_failed", "jobs_running",
    "jobs_running_expired_lease",
)
SQL = """BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT 'C2B_TENANT_JOB_COUNTS_V1:' || json_build_object(
  'probe', 'c2b-tenant-job-counts-v1',
  'read_only', current_setting('transaction_read_only') = 'on',
  'pg_major', current_setting('server_version_num')::integer / 10000,
  'privileged_scope', (
      SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user
  ),
  'tenants_total', (SELECT count(*) FROM public.business),
  'tenants_unmanaged', (
      SELECT count(*) FROM public.business b WHERE NOT EXISTS (
        SELECT 1 FROM public.pilot_launch_control p WHERE p.business_id = b.id
      )
  ),
  'pilots_total', (SELECT count(*) FROM public.pilot_launch_control),
  'pilots_running', (
      SELECT count(*) FROM public.pilot_launch_control WHERE status = 'RUNNING'
  ),
  'pilots_paused', (
      SELECT count(*) FROM public.pilot_launch_control WHERE status = 'PAUSED'
  ),
  'jobs_pending', (SELECT count(*) FROM public.persistent_job WHERE status = 'PENDING'),
  'jobs_failed', (SELECT count(*) FROM public.persistent_job WHERE status = 'FAILED'),
  'jobs_running', (SELECT count(*) FROM public.persistent_job WHERE status = 'RUNNING'),
  'jobs_running_expired_lease', (
      SELECT count(*) FROM public.persistent_job
      WHERE status = 'RUNNING' AND lease_expires_at <= now()
  )
)::text;
COMMIT;"""


class Blocked(Exception):
    pass


def command(railway: str) -> list[str]:
    encoded = base64.b64encode(SQL.encode("ascii")).decode("ascii")
    remote = (
        'set -eu; '
        '[ "${RAILWAY_PROJECT_ID:-}" = "' + PROJECT + '" ] || exit 41; '
        '[ "${RAILWAY_ENVIRONMENT_ID:-}" = "' + ENVIRONMENT + '" ] || exit 42; '
        '[ "${RAILWAY_SERVICE_ID:-}" = "' + POSTGRES_SERVICE + '" ] || exit 43; '
        'export PGPASSWORD="$POSTGRES_PASSWORD"; '
        'printf %s ' + encoded + ' | base64 -d | '
        'psql -X -q -A -t -v ON_ERROR_STOP=1 '
        '-U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    )
    return [railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
            "--service", POSTGRES_SERVICE, "--", "sh", "-c", remote]


def parse(raw: bytes) -> dict:
    if len(raw) > 4096:
        raise Blocked("C2B_OUTPUT_TOO_LARGE")
    try:
        lines = [line.strip() for line in raw.decode("utf-8", "strict").splitlines()
                 if line.strip()]
        if len(lines) != 1 or not lines[0].startswith(PREFIX):
            raise Blocked("C2B_INVALID_RESULT_FRAMING")
        result = json.loads(lines[0][len(PREFIX):])
    except (UnicodeError, ValueError, TypeError) as exc:
        raise Blocked("C2B_INVALID_RESULT_ENCODING") from exc
    if not isinstance(result, dict) or set(result) != {
        "probe", "read_only", "pg_major", "privileged_scope", *COUNTS
    }:
        raise Blocked("C2B_UNEXPECTED_RESULT_SCHEMA")
    if (result["probe"] != "c2b-tenant-job-counts-v1"
            or result["read_only"] is not True
            or result["pg_major"] != 18
            or type(result["pg_major"]) is not int
            or result["privileged_scope"] is not True):
        raise Blocked("C2B_UNVERIFIED_READ_ONLY_DB_SCOPE")
    if any(type(result[key]) is not int or result[key] < 0 for key in COUNTS):
        raise Blocked("C2B_INVALID_COUNTER")
    if (result["tenants_unmanaged"] > result["tenants_total"]
            or result["pilots_total"] > result["tenants_total"]
            or result["pilots_running"] + result["pilots_paused"] > result["pilots_total"]
            or result["jobs_running_expired_lease"] > result["jobs_running"]):
        raise Blocked("C2B_INCONSISTENT_COUNTERS")
    return result


def probe(railway: str) -> dict:
    try:
        completed = subprocess.run(command(railway), check=False,
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                   timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise Blocked("C2B_RAILWAY_READ_ONLY_UNAVAILABLE") from exc
    if completed.returncode:
        raise Blocked("C2B_RAILWAY_IDENTITY_OR_PSQL_FAILED")
    return parse(completed.stdout)


def private_evidence(data: dict) -> Path:
    root = os.environ.get("LOCALAPPDATA")
    if not root:
        raise Blocked("C2B_PRIVATE_STORAGE_UNAVAILABLE")
    folder = (Path(root) / "Helvoca" / "private-evidence").absolute()
    if any(p.is_symlink() or (p / ".git").exists() for p in (folder, *folder.parents)):
        raise Blocked("C2B_UNSAFE_PRIVATE_STORAGE")
    try:
        folder.mkdir(mode=0o700, parents=True, exist_ok=True)
        dest = folder / ("helvoca-c2b-tenant-job-"
                         + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
                         + "-" + secrets.token_hex(3) + ".private.json")
        with dest.open("x", encoding="utf-8") as file:
            json.dump({"utc": datetime.now(timezone.utc).isoformat(),
                       "scope": "Railway production PG18, read-only counts only",
                       "release_approval": False,
                       "external_effects_stopped": "UNVERIFIED",
                       "counts": data}, file, indent=2, sort_keys=True)
        return dest
    except OSError as exc:
        raise Blocked("C2B_PRIVATE_EVIDENCE_WRITE_FAILED") from exc


def main(args: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probe-read-only", action="store_true")
    options = parser.parse_args(args)
    if not options.probe_read_only:
        print("C2B_BLOCKED: C2B_EXPLICIT_READ_ONLY_OPT_IN_REQUIRED")
        return 2
    try:
        railway = shutil.which("railway")
        if not railway:
            raise Blocked("C2B_RAILWAY_CLI_NOT_FOUND")
        counts = probe(railway)
        output = private_evidence(counts)
        print("C2B_TENANT_JOB_COUNTS_READ_ONLY_CAPTURED")
        for key in COUNTS:
            print("C2B_COUNT_" + key.upper() + ":" + str(counts[key]))
        print("C2B_PRIVATE_EVIDENCE: " + str(output))
        print("C2B_NO_RELEASE_APPROVAL; in-flight/provider STOP and recovery unverified")
        return 0
    except Blocked as exc:
        print("C2B_BLOCKED: " + str(exc))
        print("C2B_NO_RELEASE_APPROVAL; no mutation or provider call")
        return 2


if __name__ == "__main__":
    sys.exit(main())
