#!/usr/bin/env python3
"""Reconstitute production PG global runtime roles and ACLs in isolated DR only.

The production connection performs schema-only pg_dump (never data or DDL).
Only GRANT/REVOKE/ALTER DEFAULT PRIVILEGES statements for the two pinned
Helvoca NOLOGIN roles are sent to DR. No customer rows or credentials output.
Run once with --execute from the custodian's authenticated Railway CLI.
"""
from __future__ import annotations
import argparse
import re
import shutil
import subprocess
import sys

PROD_PROJECT = '41988ee6-f9f6-48eb-b726-abb973bb5740'
PROD_ENV = '8bbc755a-58b5-46e8-8227-eefa4adbbce4'
PROD_SERVICE = '107bb612-f25d-4c57-98a4-f494ea6b9cb4'
DR_PROJECT = 'afd86756-415b-497a-b6fa-880b202bc581'
DR_ENV = 'c14224a8-d251-40c4-89b2-3aad83f836b9'
ROLES = ('helvoca_runtime','helvoca_system')

class Blocked(RuntimeError):
    pass

def require(ok: bool, reason: str) -> None:
    if not ok:
        raise Blocked(reason)

def cli() -> str:
    executable = shutil.which('railway')
    require(bool(executable), 'C1_RAILWAY_CLI_MISSING')
    return executable

def guard(project: str, env: str, service: str) -> str:
    return ('set -eu; '
            'test "${RAILWAY_PROJECT_ID:-}" = "' + project + '"; '
            'test "${RAILWAY_ENVIRONMENT_ID:-}" = "' + env + '"; '
            'test "${RAILWAY_SERVICE_ID:-}" = "' + service + '"; '
            'test "${POSTGRES_USER:-}" = "postgres"; '
            'test "${POSTGRES_DB:-}" = "railway"; '
            'test -n "${POSTGRES_PASSWORD:-}"; '
            'export PGPASSWORD="$POSTGRES_PASSWORD"; ')

def argv(project: str, env: str, service: str, command: str) -> list[str]:
    return [cli(),'ssh','--project',project,'--environment',env,
            '--service',service,'--','sh','-c',command]

def transfer(args: list[str], *, input_bytes: bytes | None = None,
             timeout: int = 180, cap: int = 16_000_000) -> bytes:
    try:
        r = subprocess.run(args, input=input_bytes, stdout=subprocess.PIPE,
                           stderr=subprocess.DEVNULL, check=False, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise Blocked('C1_RAILWAY_SSH_FAILED_OR_TIMEOUT') from None
    require(r.returncode == 0, 'C1_REMOTE_COMMAND_FAILED')
    require(len(r.stdout) <= cap, 'C1_REMOTE_OUTPUT_TOO_LARGE')
    return r.stdout

def extract_acl(schema_sql: bytes) -> list[str]:
    require(500 < len(schema_sql) <= 16_000_000, 'C1_SCHEMA_METADATA_UNEXPECTED_SIZE')
    try:
        data = schema_sql.decode('utf-8')
    except UnicodeDecodeError:
        raise Blocked('C1_SCHEMA_METADATA_NOT_UTF8') from None
    require('PostgreSQL database dump' in data, 'C1_SCHEMA_DUMP_HEADER_MISSING')
    matches = []
    for line in data.splitlines():
        statement = line.strip()
        if not re.search(r'\bhelvoca_(?:runtime|system)\b', statement):
            continue
        if not re.match(r'^(?:GRANT\s|REVOKE\s|ALTER DEFAULT PRIVILEGES\s)', statement):
            continue
        require(statement.count(';') == 1 and statement.endswith(';')
                and '\x00' not in statement and '--' not in statement
                and '/*' not in statement and '*/' not in statement,
                'C1_UNSAFE_OR_MULTILINE_ACL')
        matches.append(statement)
    require(len(matches) >= 8, 'C1_SOURCE_ACLS_MISSING_OR_INCOMPLETE')
    require(any(re.search(r'\bGRANT\s+.*\bhelvoca_runtime\b', s) for s in matches)
            and any(re.search(r'\bGRANT\s+.*\bhelvoca_system\b', s) for s in matches),
            'C1_BOTH_ROLE_GRANTS_REQUIRED')
    require(not any('flyway_schema_history' in x and x.startswith('GRANT') for x in matches),
            'C1_FLYWAY_HISTORY_EXPOSURE_BLOCKED')
    return matches

def compose_acl_sql(grants: list[str]) -> bytes:
    # Role definitions are taken from immutable V40__postgresql_rls_hardening.sql.
    role_setup = '''DO $helvoca_dr$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='helvoca_runtime') THEN
        CREATE ROLE helvoca_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='helvoca_system') THEN
        CREATE ROLE helvoca_system NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
    IF (SELECT COUNT(*) FROM pg_roles WHERE rolname IN ('helvoca_runtime','helvoca_system')
        AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
        AND NOT rolinherit AND NOT rolbypassrls) <> 2 THEN
        RAISE EXCEPTION 'UNSAFE_DR_ROLE_ATTRIBUTES';
    END IF;
END $helvoca_dr$;
GRANT helvoca_runtime, helvoca_system TO CURRENT_USER;
'''
    body = 'BEGIN;\n' + role_setup + '\n'.join(grants) + '''
DO $helvoca_chk$
BEGIN
    IF NOT has_table_privilege('helvoca_runtime','public.business','SELECT') THEN
        RAISE EXCEPTION 'RUNTIME_BUSINESS_SELECT_MISSING';
    END IF;
    IF NOT has_table_privilege('helvoca_system','public.business','SELECT') THEN
        RAISE EXCEPTION 'SYSTEM_BUSINESS_SELECT_MISSING';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='public.business'::regclass
          AND relrowsecurity AND relforcerowsecurity) THEN
        RAISE EXCEPTION 'BUSINESS_RLS_NOT_FORCED';
    END IF;
END $helvoca_chk$;
COMMIT;
'''
    return body.encode('utf-8')

def run(dr_service: str) -> int:
    probe_source = guard(PROD_PROJECT, PROD_ENV, PROD_SERVICE) + ('printf "C1_SOURCE_ONLY_READ_VERIFIED\\n"')
    probe_dr = guard(DR_PROJECT, DR_ENV, dr_service) + ('printf "C1_DR_TARGET_VERIFIED\\n"')
    source = transfer(argv(PROD_PROJECT, PROD_ENV, PROD_SERVICE, probe_source),cap=1000)
    require(source.strip().endswith(b'C1_SOURCE_ONLY_READ_VERIFIED'), 'C1_SOURCE_IDENTITY_MISMATCH')
    target = transfer(argv(DR_PROJECT, DR_ENV, dr_service, probe_dr),cap=1000)
    require(target.strip().endswith(b'C1_DR_TARGET_VERIFIED'), 'C1_DR_IDENTITY_MISMATCH')
    # Read metadata only, no table rows. ACLs originate from source PG and remain private.
    command = guard(PROD_PROJECT, PROD_ENV, PROD_SERVICE) + (
        'exec pg_dump --schema-only --no-owner --format=plain --encoding=UTF8 '
        '-U "$POSTGRES_USER" -d "$POSTGRES_DB"')
    schema = transfer(argv(PROD_PROJECT, PROD_ENV, PROD_SERVICE, command),timeout=300)
    grants = extract_acl(schema)
    # Repeat destination identity check immediately before changing DR only.
    target = transfer(argv(DR_PROJECT, DR_ENV, dr_service, probe_dr),cap=1000)
    require(target.strip().endswith(b'C1_DR_TARGET_VERIFIED'), 'C1_DR_CHANGED_BEFORE_ACL_RESTORE')
    target_command = guard(DR_PROJECT, DR_ENV, dr_service) + (
        'exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"')
    transfer(argv(DR_PROJECT, DR_ENV, dr_service, target_command),
             input_bytes=compose_acl_sql(grants),timeout=240,cap=3000)
    # Adversarial tenant role probe: no tenant scope => business must be invisible.
    verify_command = guard(DR_PROJECT, DR_ENV, dr_service) + (
        'exec psql -X -A -t -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"')
    query = b'BEGIN; SET LOCAL ROLE helvoca_runtime; SELECT COUNT(*) FROM public.business; ROLLBACK;\n'
    observed = transfer(argv(DR_PROJECT, DR_ENV, dr_service, verify_command),
                        input_bytes=query,timeout=90,cap=3000).decode('ascii','replace')
    require('0' in observed.splitlines(), 'C1_DR_TENANT_NO_SCOPE_ACCESS_BLOCKED')
    print('C1_DR_GLOBAL_ROLES_AND_SOURCE_ACLS_RESTORED_VERIFIED; acl_statements=' + str(len(grants)))
    print('Only isolated DR roles and grants changed; no customer rows read from production.')
    return 0

def main(argv_: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--dr-service', default='')
    args = parser.parse_args(argv_)
    if not args.execute:
        print('C1_BLOCKED: EXPLICIT_EXECUTION_REQUIRED')
        return 2
    try:
        require(bool(re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',
                                  args.dr_service)) and args.dr_service != PROD_SERVICE
                and args.dr_service != '00000000-0000-0000-0000-000000000000',
                'C1_DR_SERVICE_ID_INVALID')
        return run(args.dr_service)
    except (Blocked, OSError, ValueError) as ex:
        code = str(ex) if isinstance(ex, Blocked) else 'C1_PRIVATE_ACL_OPERATION_FAILED'
        print('C1_BLOCKED: '+code)
        print('Production export was READ-ONLY. Do not blindly retry. DR still billable.')
        return 2

if __name__ == '__main__':
    sys.exit(main())
