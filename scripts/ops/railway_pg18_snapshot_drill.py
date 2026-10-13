#!/usr/bin/env python3
"""Read-only Railway Postgres dump and disposable local PG18 restore drill.

Requires a locally authenticated Railway CLI with SSH access. Optional Docker
Desktop supplies an independent, network-disabled Postgres 18 restore target.
Never creates a database on Railway, changes production, or uploads its dump.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time

PROJECT = "41988ee6-f9f6-48eb-b726-abb973bb5740"
ENVIRONMENT = "8bbc755a-58b5-46e8-8227-eefa4adbbce4"
SERVICE = "107bb612-f25d-4c57-98a4-f494ea6b9cb4"
CORE = ("business", "app_user", "booking", "business_operation", "business_payment")
SSH_DUMP = ('export PGPASSWORD="$POSTGRES_PASSWORD"; '
            'exec pg_dump --format=custom --no-owner --no-acl '
            '-U "$POSTGRES_USER" -d "$POSTGRES_DB"')


def private_directory(destination: Path) -> Path:
    destination = destination.expanduser().absolute()
    for ancestor in (destination, *destination.parents):
        if ancestor.is_symlink():
            raise ValueError("Refusing symlink destination or parent")
        if (ancestor / ".git").exists():
            raise ValueError("Backups must never be saved inside a Git repository")
    destination.mkdir(mode=0o700, parents=True, exist_ok=True)
    if destination.is_symlink() or not destination.is_dir():
        raise ValueError("Unsafe output destination")
    if os.name != "nt":
        destination.chmod(0o700)
    return destination


def validate_dump(path: Path) -> dict:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        if stream.read(5) != b"PGDMP":
            raise ValueError("Not a PostgreSQL custom-format dump")
        stream.seek(0)
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    if size < 1024:
        raise ValueError("PostgreSQL dump unexpectedly small")
    return {"bytes": size, "sha256": digest.hexdigest()}


def run_quiet(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, stdin=kwargs.pop("stdin", subprocess.DEVNULL),
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                          timeout=kwargs.pop("timeout", 45), check=False, **kwargs)


def dump_from_railway(railway: str, path: Path) -> dict:
    cmd = [railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
           "--service", SERVICE, "--", "sh", "-c", SSH_DUMP]
    try:
        with path.open("xb") as stream:
            if os.name != "nt":
                path.chmod(0o600)
            with subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=stream,
                                  stderr=subprocess.DEVNULL) as process:
                try:
                    code = process.wait(timeout=300)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                    raise RuntimeError("Railway export timeout")
        if code != 0:
            raise RuntimeError("Railway SSH/pg_dump failed")
        return validate_dump(path)
    except Exception:
        path.unlink(missing_ok=True)
        raise


def query_number(docker: str, container: str, sql: str) -> int:
    r = run_quiet([docker, "exec", container, "psql", "-X", "-A", "-t",
                   "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d",
                   "helvoca_restore", "-c", sql])
    if r.returncode != 0:
        raise RuntimeError("Local verification query failed")
    try:
        return int(r.stdout.decode("utf-8").strip())
    except ValueError as exc:
        raise RuntimeError("Unexpected local verification result") from exc


def restore_to_disposable_docker(docker: str, dump: Path) -> dict:
    """No port publishing, no shared volumes, and no connection to production."""
    name = "helvoca-restore-" + secrets.token_hex(5)
    created = False
    try:
        r = run_quiet([docker, "run", "--detach", "--rm", "--network", "none",
                       "--name", name, "-e", "POSTGRES_HOST_AUTH_METHOD=trust",
                       "-e", "POSTGRES_DB=helvoca_restore", "postgres:18"],
                      timeout=180)
        if r.returncode != 0:
            raise RuntimeError("Could not start isolated local PostgreSQL 18")
        created = True
        for _ in range(40):
            r = run_quiet([docker, "exec", name, "pg_isready", "-U", "postgres",
                           "-d", "helvoca_restore"], timeout=10)
            if r.returncode == 0:
                break
            time.sleep(2)
        else:
            raise RuntimeError("Local PostgreSQL 18 did not become ready")
        for args in (["pg_restore", "--list"],
                     ["pg_restore", "--exit-on-error", "--no-owner", "--no-acl",
                      "-U", "postgres", "-d", "helvoca_restore"]):
            with dump.open("rb") as archive:
                p = run_quiet([docker, "exec", "-i", name, *args],
                              stdin=archive, timeout=240)
            if p.returncode != 0:
                raise RuntimeError("Local pg_restore listing or restore failed")
        tables = query_number(docker, name,
            "SELECT COUNT(*) FROM pg_tables WHERE schemaname='public'")
        core = query_number(docker, name,
            "SELECT COUNT(*) FROM (VALUES " + ",".join(f"('{t}')" for t in CORE) +
            ") AS t(n) WHERE to_regclass('public.' || n) IS NOT NULL")
        history = query_number(docker, name,
            "SELECT COUNT(*) FROM public.flyway_schema_history")
        policies = query_number(docker, name,
            "SELECT COUNT(*) FROM pg_policies WHERE schemaname='public'")
        rows = {table: query_number(docker, name,
                f'SELECT COUNT(*) FROM public."{table}"') for table in CORE}
        if tables < len(CORE) or core != len(CORE) or history < 1:
            raise RuntimeError("Local restore missing required schema or Flyway history")
        return {"public_tables": tables, "core_tables": core,
                "flyway_history_rows": history, "rls_policies": policies,
                "core_table_rows": rows}
    finally:
        if created:
            removed = run_quiet([docker, "rm", "--force", name], timeout=30)
            if removed.returncode != 0:
                raise RuntimeError("Could not destroy local restore container")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    windows_local = Path(os.environ.get("LOCALAPPDATA") or (Path.home() / "AppData" / "Local"))
    default = (windows_local / "Helvoca" / "private-backups"
               if os.name == "nt" else Path.home() / ".local/share/helvoca/private-backups")
    parser.add_argument("--output-dir", type=Path, default=default)
    args = parser.parse_args(argv)
    railway = shutil.which("railway")
    if not railway:
        print("BLOCKED: Install Railway CLI and sign in locally (no secrets in chat).")
        return 2
    try:
        directory = private_directory(args.output_dir)
    except (OSError, ValueError):
        print("BLOCKED: Could not create private local backup directory.")
        return 2
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    dump = directory / f"helvoca-postgres18-{stamp}-{secrets.token_hex(3)}.dump"
    report = {"time_utc": stamp, "database_source": "Helvoca Railway production (read only)",
              "production_mutated": False, "restored_outside_production": False,
              "schema_integrity_verified": False, "commercial_go": False}
    try:
        info = dump_from_railway(railway, dump)
        report["archive"] = {"file": dump.name, **info}
    except (OSError, RuntimeError, ValueError) as err:
        print("BLOCKED: Could not create valid private pg_dump archive. " + str(err))
        return 2
    docker = shutil.which("docker")
    if docker:
        try:
            result = restore_to_disposable_docker(docker, dump)
            report["restored_outside_production"] = True
            report["schema_integrity_verified"] = True
            report["restored_summary"] = result
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
            report["restore_blocker"] = str(exc)
    else:
        report["restore_blocker"] = "Docker Desktop CLI not installed"
    private_report = directory / (dump.stem + ".private.json")
    with private_report.open("x", encoding="utf-8") as out:
        if os.name != "nt":
            private_report.chmod(0o600)
        json.dump(report, out, indent=2)
    print("Backup stored ONLY on this computer: " + str(dump))
    print("SHA-256: " + info["sha256"])
    if report["schema_integrity_verified"]:
        print("RESTORE VERIFIED in network-disabled disposable PostgreSQL 18.")
        print("Local evidence: " + str(private_report))
        return 0
    print("BACKUP CREATED; RESTORE NOT CERTIFIED: " + report["restore_blocker"])
    print("Local evidence: " + str(private_report))
    return 3


if __name__ == "__main__":
    sys.exit(main())
