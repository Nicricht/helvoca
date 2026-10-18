#!/usr/bin/env python3
"""Synthetic PG18 integration: exercise actual C1 role/ACL SQL and two-tenant RLS.

Only 127.0.0.1:5432/helvoca_c1_ci is accepted. No Railway access, no real
data, no secrets, no services outside GitHub Actions.
"""
from __future__ import annotations
import os
import subprocess
import sys
import c1_restore_global_roles_acls as recovery

def require(ok: bool, code: str) -> None:
    if not ok:
        raise RuntimeError(code)

def psql(sql: str, *, expected_failure: bool = False) -> str:
    env = os.environ.copy()
    env["PGPASSWORD"] = "helvoca_c1_synthetic_postgres_ci_only"
    try:
        result = subprocess.run(
            ["psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
             "-h", "127.0.0.1", "-p", "5432", "-U", "postgres",
             "-d", "helvoca_c1_ci"],
            input=sql, text=True, capture_output=True, check=False,
            timeout=45, env=env,
        )
    except (OSError, subprocess.TimeoutExpired):
        raise RuntimeError("C1_PG18_LOCAL_CLIENT_UNAVAILABLE") from None
    if expected_failure:
        require(result.returncode != 0, "C1_EXPECTED_MISSING_ROLE_RED")
        return ""
    require(result.returncode == 0, "C1_PG18_SYNTHETIC_SQL_ASSERTION_FAILED")
    return result.stdout.strip()

def synthetic_acl() -> bytes:
    statements = ["-- PostgreSQL database dump"]
    for _ in range(3):
        statements.extend([
            "GRANT USAGE ON SCHEMA public TO helvoca_runtime;",
            "GRANT USAGE ON SCHEMA public TO helvoca_system;",
            "GRANT SELECT, INSERT ON TABLE public.business TO helvoca_runtime;",
            "GRANT SELECT ON TABLE public.business TO helvoca_system;",
        ])
    return ("\n".join(statements) + "\n" +
            "-- synthetic metadata only\n" * 20).encode("utf-8")

def main() -> int:
    require(os.environ.get("CI") == "true"
            and os.environ.get("C1_PG18_SYNTHETIC_CI") == "1",
            "C1_SYNTHETIC_CI_EXPLICIT_OPT_IN_REQUIRED")
    require(psql("SELECT current_database() || ':' || "
                 "(current_setting('server_version_num')::int/10000);")
            == "helvoca_c1_ci:18", "C1_WRONG_DATABASE_OR_VERSION")
    require(psql("SELECT count(*) FROM pg_roles WHERE rolname IN "
                 "('helvoca_runtime','helvoca_system');") == "0",
            "C1_EXPECTED_CLEAN_PG18_GLOBAL_ROLES")

    psql("""
        CREATE TABLE public.business (id uuid PRIMARY KEY);
        INSERT INTO public.business VALUES
          ('11111111-1111-4111-8111-111111111111'),
          ('22222222-2222-4222-8222-222222222222');
        CREATE FUNCTION public.helvoca_rls_business_id() RETURNS uuid
          LANGUAGE sql STABLE
          AS $$ SELECT NULLIF(current_setting('app.tenant_id',true),'')::uuid $$;
        ALTER TABLE public.business ENABLE ROW LEVEL SECURITY;
        ALTER TABLE public.business FORCE ROW LEVEL SECURITY;
        CREATE POLICY helvoca_business_isolation ON public.business
          USING (current_user='postgres' OR current_user='helvoca_system'
                 OR id=public.helvoca_rls_business_id())
          WITH CHECK (current_user='postgres' OR current_user='helvoca_system'
                 OR id=public.helvoca_rls_business_id());
    """)
    # RED before helper: a pg_dump alone does not restore global roles.
    psql("BEGIN; SET LOCAL ROLE helvoca_runtime; "
         "SELECT count(*) FROM public.business; ROLLBACK;",
         expected_failure=True)
    print("C1_PG18_GLOBAL_ROLE_MISSING_EXPECTED_RED=PASS")

    extracted = recovery.extract_acl(synthetic_acl())
    require(len(extracted) == 12, "C1_SYNTHETIC_ACL_NOT_COMPLETE")
    restoration = recovery.compose_acl_sql(extracted).decode("utf-8")
    psql(restoration)
    psql(restoration)  # Ensure role creation is idempotent.

    psql("""
        DO $check$
        DECLARE n int;
        BEGIN
          SELECT count(*) INTO n FROM pg_roles
           WHERE rolname IN ('helvoca_runtime','helvoca_system')
             AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb
             AND NOT rolcreaterole AND NOT rolinherit AND NOT rolbypassrls;
          IF n <> 2 THEN RAISE EXCEPTION 'UNSAFE_ROLE_ATTRIBUTES'; END IF;
          IF NOT has_table_privilege('helvoca_runtime','public.business','SELECT')
             OR NOT has_table_privilege('helvoca_runtime','public.business','INSERT')
             OR has_table_privilege('helvoca_runtime','public.business','UPDATE')
             OR has_table_privilege('helvoca_system','public.business','DELETE')
          THEN RAISE EXCEPTION 'WRONG_ACL'; END IF;
        END $check$;
        BEGIN;
        SET LOCAL ROLE helvoca_runtime;
        DO $tenant$
        DECLARE n int;
        BEGIN
          SELECT count(*) INTO n FROM public.business;
          IF n <> 0 THEN RAISE EXCEPTION 'NO_SCOPE_LEAK'; END IF;
          PERFORM set_config('app.tenant_id',
                           '11111111-1111-4111-8111-111111111111', true);
          SELECT count(*) INTO n FROM public.business;
          IF n <> 1 THEN RAISE EXCEPTION 'TENANT_A_VISIBLE_COUNT'; END IF;
          SELECT count(*) INTO n FROM public.business
            WHERE id='22222222-2222-4222-8222-222222222222';
          IF n <> 0 THEN RAISE EXCEPTION 'TENANT_A_READ_B'; END IF;
          BEGIN
            INSERT INTO public.business
               VALUES ('33333333-3333-4333-8333-333333333333');
            RAISE EXCEPTION 'CROSS_TENANT_INSERT_ACCEPTED';
          EXCEPTION WHEN insufficient_privilege THEN
            IF position('row-level security policy' in SQLERRM)=0 THEN
              RAISE EXCEPTION 'NOT_AN_RLS_DENIAL';
            END IF;
          END;
          PERFORM set_config('app.tenant_id',
                           '22222222-2222-4222-8222-222222222222', true);
          SELECT count(*) INTO n FROM public.business;
          IF n <> 1 THEN RAISE EXCEPTION 'TENANT_B_VISIBLE_COUNT'; END IF;
          SELECT count(*) INTO n FROM public.business
            WHERE id='11111111-1111-4111-8111-111111111111';
          IF n <> 0 THEN RAISE EXCEPTION 'TENANT_B_READ_A'; END IF;
        END $tenant$;
        RESET ROLE;
        SET LOCAL ROLE helvoca_system;
        DO $system$
        DECLARE n int;
        BEGIN
          SELECT count(*) INTO n FROM public.business;
          IF n <> 2 THEN RAISE EXCEPTION 'SYSTEM_MUST_SEE_BOTH'; END IF;
        END $system$;
        ROLLBACK;
    """)
    require(psql("SELECT count(*) FROM public.business;") == "2",
            "C1_SYNTHETIC_ROWS_CHANGED")
    print("C1_PG18_ROLES_ACL_TWO_TENANTS_REAL_DATABASE=PASS")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, recovery.Blocked) as err:
        print("C1_PG18_SYNTHETIC_INTEGRATION_BLOCKED: " + str(err))
        sys.exit(2)
