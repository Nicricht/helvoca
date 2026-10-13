#!/usr/bin/env python3
"""Helvoca Stage D: READ-ONLY Flyway / PostgreSQL 18 production preflight.

Runs ONLY a hard-coded BEGIN TRANSACTION READ ONLY + catalog/Flyway SELECT via
Railway SSH into the existing Postgres service. No SQL from users or CLI args.
No connection to Twilio, no calls, no dumps, no DB writes, no deploys.
Never prints raw SSH/psql output or customer data. Stores metadata privately.
"""
from __future__ import annotations

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import shlex
import shutil
import subprocess
import sys

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
POSTGRES_SERVICE = "107bb612-f25d-4c57-98a4-f494ea6b9cb4"
RESULT_PREFIX = "STAGE_D_FLYWAY_PROBE:"
REQUIRED_KEYS = (
    "probe", "read_only", "pg_major", "history_count", "v99_success_count",
    "later_voice_migrations", "history_failed", "last_version",
    "provenance_columns", "future_tables",
)
# Only trusted read-only statements; no customer SELECT or phone identifiers.
# This is executed by psql as a single -c in one database session.
SQL = """BEGIN TRANSACTION READ ONLY;
SELECT 'STAGE_D_FLYWAY_PROBE:' || json_build_object(
    'probe', 'stage-d-flyway-catalog-v1',
    'read_only', current_setting('transaction_read_only') = 'on',
    'pg_major', (current_setting('server_version_num')::integer / 10000),
    'history_count', (SELECT count(*) FROM public.flyway_schema_history),
    'v99_success_count', (
        SELECT count(*) FROM public.flyway_schema_history
         WHERE version = '99' AND success
    ),
    'later_voice_migrations', (
        SELECT count(*) FROM public.flyway_schema_history
         WHERE version IN ('100','101','102','103')
    ),
    'history_failed', (
        SELECT count(*) FROM public.flyway_schema_history WHERE NOT success
    ),
    'last_version', (
        SELECT version FROM public.flyway_schema_history
         ORDER BY installed_rank DESC LIMIT 1
    ),
    'provenance_columns', (
        SELECT count(*) FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'phone_number'
           AND column_name IN ('twilio_binding_source','twilio_account_sid','voice_verified_at')
    ),
    'future_tables', (
        SELECT count(*) FROM (VALUES
            ('twilio_existing_enrollment_claim'),
            ('twilio_existing_enrollment_request'),
            ('twilio_existing_enrollment_audit')
        ) AS t(tablename)
         WHERE to_regclass('public.' || t.tablename) IS NOT NULL
    )
)::text;
COMMIT;"""


def private_directory() -> Path:
    value = os.environ.get("LOCALAPPDATA")
    if not value:
        raise RuntimeError("LOCALAPPDATA is not configured")
    dest = (Path(value) / "Helvoca" / "private-backups").absolute()
    for part in (dest, *dest.parents):
        if part.is_symlink() or (part / ".git").exists():
            raise RuntimeError("Evidence directory is unsafe (symlink or Git repository)")
    dest.mkdir(mode=0o700, parents=True, exist_ok=True)
    return dest


def railway_command(railway: str) -> list[str]:
    # POSIX quoting applies to the shell INSIDE the Railway Postgres container,
    # not to PowerShell. Never pass the remote DB password in CLI arguments.
    remote = (
        'export PGPASSWORD="$POSTGRES_PASSWORD"; '
        'exec psql -X -q -A -t -v ON_ERROR_STOP=1 '
        '-U "$POSTGRES_USER" -d "$POSTGRES_DB" '
        '-c ' + shlex.quote(SQL)
    )
    return [
        railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
        "--service", POSTGRES_SERVICE, "--", "sh", "-c", remote,
    ]


def parse_result(stdout: bytes) -> dict:
    if len(stdout) > 32_000:
        raise RuntimeError("Unexpectedly large probe response")
    decoded = stdout.decode("utf-8", errors="strict")
    matches = [line[len(RESULT_PREFIX):].strip() for line in decoded.splitlines()
               if line.strip().startswith(RESULT_PREFIX)]
    if len(matches) != 1:
        raise RuntimeError("Expected exactly one safe catalog result")
    try:
        result = json.loads(matches[0])
    except (ValueError, TypeError) as exc:
        raise RuntimeError("Invalid read-only catalog response") from exc
    if not isinstance(result, dict) or any(key not in result for key in REQUIRED_KEYS):
        raise RuntimeError("Catalog response is incomplete")
    numeric = ("pg_major", "history_count", "v99_success_count",
               "later_voice_migrations", "history_failed",
               "provenance_columns", "future_tables")
    if any(type(result[key]) is not int or result[key] < 0 for key in numeric):
        raise RuntimeError("Catalog counters have invalid types")
    if result["probe"] != "stage-d-flyway-catalog-v1" or type(result["read_only"]) is not bool:
        raise RuntimeError("Read-only catalog response has invalid provenance")
    if result["last_version"] is not None and (
        not isinstance(result["last_version"], str)
        or len(result["last_version"]) > 24
    ):
        raise RuntimeError("Last migration version has invalid type")
    # Keep only a fixed set of *metadata*; ignore accidental future fields.
    return {key: result[key] for key in REQUIRED_KEYS}


def evaluate(info: dict) -> tuple[bool, list[str]]:
    blockers = []
    checks = (
        ("NOT_READ_ONLY", info["read_only"] is True),
        ("POSTGRES_NOT_18", info["pg_major"] == 18),
        ("EMPTY_FLYWAY_HISTORY", info["history_count"] >= 99),
        ("V99_NOT_APPLIED_ONCE", info["v99_success_count"] == 1),
        ("FUTURE_V100_V103_ALREADY_PRESENT", info["later_voice_migrations"] == 0),
        ("FAILED_FLYWAY_MIGRATION_EXISTS", info["history_failed"] == 0),
        ("LAST_FLYWAY_VERSION_NOT_99", info["last_version"] == "99"),
        ("UNEXPECTED_V100_COLUMNS", info["provenance_columns"] == 0),
        ("UNEXPECTED_V102_V103_TABLES", info["future_tables"] == 0),
    )
    for reason, passed in checks:
        if not passed:
            blockers.append(reason)
    return not blockers, blockers


def main() -> int:
    try:
        railway = shutil.which("railway")
        if railway is None:
            raise RuntimeError("Railway CLI missing; install or add to PATH")
        cmd = railway_command(railway)
        try:
            process = subprocess.run(
                cmd, input=b"", stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL, timeout=110, check=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError("Read-only Railway query timed out") from exc
        if process.returncode != 0:
            raise RuntimeError("Railway SSH or read-only psql query failed")
        info = parse_result(process.stdout)
        good, blockers = evaluate(info)
        directory = private_directory()
        output = directory / (
            "helvoca-stage-d-read-only-flyway-"
            + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            + "-" + secrets.token_hex(3) + ".private.json"
        )
        evidence = {
            "run_utc": datetime.now(timezone.utc).isoformat(),
            "environment": "Helvoca Railway production (read-only SSH)",
            "database_modified": False,
            "probe_sql_read_only": info["read_only"],
            "read_only_catalog": info,
            "status": "PRODUCTION_FLYWAY_BASELINE_VERIFIED" if good else "BLOCKED",
            "blockers": blockers,
            "stage_d_carrier_verification": "PENDING",
            "production_deploy": "NO-GO",
            "live_call_certified": False,
            "commercial_go": False,
        }
        with output.open("x", encoding="utf-8") as f:
            json.dump(evidence, f, indent=2, sort_keys=True)
        if good:
            print("STAGE_D_PRODUCTION_FLYWAY_READ_ONLY_VERIFIED")
        else:
            print("STAGE_D_BLOCKED: catalog preflight reported incompatible baseline")
            print("Safe blockers: " + ", ".join(blockers))
        print("No changes to Railway, PostgreSQL, Twilio, or GitHub main.")
        print("Private evidence: " + str(output))
        return 0 if good else 2
    except (RuntimeError, ValueError, OSError, UnicodeError) as exc:
        # Only our own safe error categories are shown. Never raw psql
        # output, Railway stderr, DB values, credentials or customer data.
        print("STAGE_D_BLOCKED: " + str(exc).splitlines()[0])
        return 2


if __name__ == "__main__":
    sys.exit(main())
