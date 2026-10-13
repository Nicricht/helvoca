#!/usr/bin/env python3
"""Stage B: exercise V100-V103 on the existing PRIVATE restored Railway dump.

Only a disposable, network-disabled local PostgreSQL 18 Docker container is
modified. This executes SQL migrations using psql, not Flyway; the separate
GitHub Testcontainers workflow verifies Flyway on a fresh PostgreSQL 18.
Never uploads database bytes or prints SQL/DB records.
"""
from __future__ import annotations

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
from urllib.request import urlopen

# Immutable source of V100..V103, certified Stage A parent of this PR.
SQL_REF = "06ed0b2dc9a41baf2b8a02f81725dd80ce6467a9"
VERSIONS = {
    100: "V100__twilio_managed_voice_binding_provenance.sql",
    101: "V101__twilio_verified_existing_binding_provenance.sql",
    102: "V102__twilio_existing_enrollment_claim.sql",
    103: "V103__twilio_existing_enrollment_admin_approval.sql",
}
ARCHIVE = "helvoca-postgres18-20261013T041250Z-355b64.dump"
EXPECTED_SHA256 = "46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18"
GUARDED_TABLES = (
    "twilio_existing_enrollment_claim",
    "twilio_existing_enrollment_request",
    "twilio_existing_enrollment_audit",
)


def run(command: list[str], *, data: bytes | None = None, timeout: int = 90):
    # stderr intentionally never printed: it may contain private DB metadata.
    return subprocess.run(command, input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, check=False, timeout=timeout)


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise RuntimeError(reason)


def sha256_file(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def pinned_sql() -> list[tuple[int, bytes]]:
    result = []
    for version, name in VERSIONS.items():
        url = ("https://raw.githubusercontent.com/Nicricht/helvoca/" + SQL_REF
               + "/src/main/resources/db/migration/" + name)
        with urlopen(url, timeout=20) as response:
            body = response.read(500_000)
        require(100 < len(body) < 500_000, "Invalid pinned migration content")
        require(body.strip().startswith(b"--"), "Missing SQL migration header")
        result.append((version, body))
    return result


def psql(docker: str, name: str, sql: str, *, role: str | None = None) -> str:
    if role is not None:
        require(role in ("helvoca_runtime", "helvoca_system"), "Forbidden role")
        sql = "SET ROLE " + role + "; " + sql
    cmd = [docker, "exec", name, "psql", "-X", "-q", "-A", "-t",
           "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d",
           "helvoca_restore", "-c", sql]
    result = run(cmd)
    require(result.returncode == 0, "Isolated PostgreSQL verification failed")
    return result.stdout.decode("utf-8").strip()


def number(docker: str, name: str, sql: str, *, role: str | None = None) -> int:
    return int(psql(docker, name, sql, role=role))


def restore_and_rehearse(docker: str, archive: Path,
                         migrations: list[tuple[int, bytes]]) -> dict:
    container = "helvoca-stage-b-" + secrets.token_hex(6)
    created = False
    try:
        start = run([docker, "run", "-d", "--rm", "--network", "none",
                     "--name", container,
                     "-e", "POSTGRES_HOST_AUTH_METHOD=trust",
                     "-e", "POSTGRES_DB=helvoca_restore", "postgres:18"],
                    timeout=150)
        require(start.returncode == 0, "Could not create isolated PostgreSQL 18")
        created = True
        for _ in range(45):
            ready = run([docker, "exec", container, "pg_isready",
                         "-U", "postgres", "-d", "helvoca_restore"], timeout=10)
            if ready.returncode == 0:
                break
            time.sleep(2)
        else:
            raise RuntimeError("Isolated PostgreSQL 18 startup timeout")

        # Database roles are cluster-global and not part of pg_dump. Create
        # only inside this disposable container before restoring policies.
        psql(docker, container, "CREATE ROLE helvoca_runtime NOLOGIN NOBYPASSRLS;"
             "CREATE ROLE helvoca_system NOLOGIN NOBYPASSRLS;")

        for args in (["pg_restore", "--list"],
                     ["pg_restore", "--exit-on-error", "--no-owner", "--no-acl",
                      "-U", "postgres", "-d", "helvoca_restore"]):
            with archive.open("rb") as source:
                result = subprocess.run(
                    [docker, "exec", "-i", container, *args], stdin=source,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    check=False, timeout=300)
            require(result.returncode == 0, "Private database restore failed")

        require(number(docker, container,
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='99' AND success") == 1,
            "Expected successful V99 baseline is missing; stop before migration")
        require(number(docker, container,
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN "
            "('100','101','102','103')") == 0,
            "Source is not a pre-V100 baseline; no automatic reapplication")
        phone_before = psql(docker, container,
            "SELECT md5(COALESCE(string_agg(t::text, ',' ORDER BY t.id),'') ) "
            "FROM public.phone_number t")
        business_before = number(docker, container, "SELECT COUNT(*) FROM public.business")
        phone_count = number(docker, container, "SELECT COUNT(*) FROM public.phone_number")

        # One transaction per version (Flyway-compatible PostgreSQL DDL);
        # no production connections, no migration to the original archive.
        for version, sql in migrations:
            require(version in VERSIONS, "Unrecognized migration version")
            result = run([docker, "exec", "-i", container, "psql", "-X",
                          "-v", "ON_ERROR_STOP=1", "--single-transaction",
                          "-U", "postgres", "-d", "helvoca_restore"], data=sql)
            require(result.returncode == 0,
                    "Isolated SQL migration V" + str(version) + " failed")

        phone_after = psql(docker, container,
            "SELECT md5(COALESCE(string_agg(t::text, ',' ORDER BY t.id),'') ) "
            "FROM public.phone_number t")
        require(phone_after == phone_before, "Migration changed existing phone rows")
        require(number(docker, container, "SELECT COUNT(*) FROM public.phone_number") == phone_count,
                "Migration changed phone row count")
        require(number(docker, container, "SELECT COUNT(*) FROM public.business") == business_before,
                "Migration changed business row count")
        for table in GUARDED_TABLES:
            require(number(docker, container,
                "SELECT COUNT(*) FROM pg_class WHERE oid = 'public." + table +
                "'::regclass AND relrowsecurity AND relforcerowsecurity") == 1,
                "Missing forced RLS: " + table)
            require(number(docker, container,
                "SELECT COUNT(*) FROM public." + table) == 0,
                "Migration unexpectedly populated platform-only table: " + table)
            # No grants to tenant role. This is a denied-operation assertion.
            denied = run([docker, "exec", container, "psql", "-X", "-A", "-t",
                          "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d",
                          "helvoca_restore", "-c",
                          "SET ROLE helvoca_runtime; SELECT COUNT(*) FROM public." + table])
            require(denied.returncode != 0, "Tenant can read platform table: " + table)

        require(number(docker, container,
            "SELECT COUNT(*) FROM pg_trigger WHERE tgname IN "
            "('trg_phone_verified_existing_write_guard',"
            "'trg_twilio_existing_request_immutable_guard') AND NOT tgisinternal") == 2,
            "Required database guard triggers are absent")
        require(number(docker, container,
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' "
            "AND indexname IN ('uq_phone_managed_twilio_account_sid',"
            "'uq_phone_twilio_certified_external',"
            "'idx_twilio_existing_request_phone','idx_twilio_existing_audit_change')") == 4,
            "Required enrollment indexes are absent")

        # Test the restored copy's existing tenant root row isolation without
        # selecting or publishing any user data. Grants are scratch-only because
        # pg_restore --no-acl intentionally discards source table ACLs.
        psql(docker, container, "GRANT SELECT ON public.business TO helvoca_runtime")
        isolation_empty = number(docker, container,
            "SET app.tenant_id = '00000000-0000-4000-8000-000000000000';"
            "SELECT COUNT(*) FROM public.business", role="helvoca_runtime")
        require(isolation_empty == 0, "Tenant without matching ID can see businesses")
        tenant_ids = psql(docker, container,
            "SELECT id::text FROM public.business ORDER BY id LIMIT 2").splitlines()
        tenants_verified = 0
        for identifier in tenant_ids:
            # UUIDs originate from the isolated database; validate to avoid SQL injection.
            import uuid
            identifier = str(uuid.UUID(identifier))
            visible = number(docker, container,
                "SET app.tenant_id = '" + identifier + "';"
                "SELECT COUNT(*) FROM public.business", role="helvoca_runtime")
            require(visible == 1, "Tenant can see businesses outside its own identity")
            tenants_verified += 1
        return {"v100_v103_sql_applied": True, "tenant_role_platform_tables_denied": True,
                "phone_rows_unchanged": True, "business_rows_unchanged": True,
                "forced_rls_tables": len(GUARDED_TABLES),
                "tenant_identities_checked": tenants_verified,
                "flyway_executed_on_restored_copy": False}
    finally:
        if created:
            deleted = run([docker, "rm", "-f", container], timeout=30)
            require(deleted.returncode == 0, "Could not remove isolated test container")


def main() -> int:
    base = (Path(os.environ.get("LOCALAPPDATA", ""))
            / "Helvoca" / "private-backups")
    archive = base / ARCHIVE
    try:
        require(archive.is_file() and not archive.is_symlink(),
                "Attested private archive not found")
        require(sha256_file(archive) == EXPECTED_SHA256,
                "Private archive SHA-256 does not match Stage A")
        docker = shutil.which("docker")
        require(docker is not None, "Docker Desktop is required")
        migrations = pinned_sql()
        report = restore_and_rehearse(docker, archive, migrations)
        require(sha256_file(archive) == EXPECTED_SHA256,
                "Unexpected source archive change")
        output = base / "helvoca-stage-b-v100-v103.private.json"
        payload = {"timestamp_utc": datetime.now(timezone.utc).isoformat(),
                   "backup_sha256": EXPECTED_SHA256,
                   "migration_ref": SQL_REF,
                   "restore_destination": "disposable local PostgreSQL 18",
                   "production_modified": False, "commercial_go": False,
                   **report}
        with output.open("x", encoding="utf-8") as stream:
            json.dump(payload, stream, indent=2)
        print("LOCAL_PG18_MIGRATION_REHEARSAL_VERIFIED")
        print("Applied V100-V103 on restored private copy; RLS/constraints verified.")
        print("This is NOT a Flyway execution on the restored copy.")
        print("Private evidence: " + str(output))
        return 0
    except Exception as exc:
        # Error bodies are never printed, preventing accidental data leakage.
        if isinstance(exc, FileExistsError):
            print("BLOCKED: local Stage B evidence already exists; inspect it privately.")
        elif isinstance(exc, (RuntimeError, ValueError, OSError, subprocess.TimeoutExpired)):
            print("BLOCKED: " + str(exc).splitlines()[0])
        else:
            print("BLOCKED: unexpected local rehearsal failure; no production changes.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
