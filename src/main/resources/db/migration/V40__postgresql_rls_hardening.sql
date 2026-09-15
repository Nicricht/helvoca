-- V40: PostgreSQL row-level tenant isolation.
--
-- Flyway continues to use the database owner connection. Runtime application
-- connections SET ROLE to one of these NOLOGIN/NOBYPASSRLS roles.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'helvoca_runtime') THEN
        CREATE ROLE helvoca_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    ELSE
        ALTER ROLE helvoca_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'helvoca_system') THEN
        CREATE ROLE helvoca_system NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    ELSE
        ALTER ROLE helvoca_system NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

GRANT helvoca_runtime TO CURRENT_USER;
GRANT helvoca_system TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO helvoca_runtime, helvoca_system;

-- Normal tenant runtime can read global reference tables but cannot mutate them.
GRANT SELECT ON ALL TABLES IN SCHEMA public TO helvoca_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO helvoca_runtime;

-- Controlled system paths need the existing application capabilities across tenants.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO helvoca_system;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO helvoca_system;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO helvoca_runtime, helvoca_system;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO helvoca_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO helvoca_system;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO helvoca_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO helvoca_system;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO helvoca_runtime, helvoca_system;

CREATE OR REPLACE FUNCTION public.helvoca_rls_business_id()
RETURNS uuid
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    raw_value text;
BEGIN
    raw_value := current_setting('app.tenant_id', true);
    IF raw_value IS NULL OR btrim(raw_value) = '' THEN
        RETURN NULL;
    END IF;
    BEGIN
        RETURN raw_value::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RETURN NULL;
    END;
END
$$;

GRANT EXECUTE ON FUNCTION public.helvoca_rls_business_id() TO helvoca_runtime, helvoca_system;

-- Apply RLS to every existing public table carrying a UUID business_id.
DO $$
DECLARE
    table_row record;
    migration_owner text := current_user;
BEGIN
    FOR table_row IN
        SELECT c.table_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_schema = c.table_schema
           AND t.table_name = c.table_name
         WHERE c.table_schema = 'public'
           AND c.column_name = 'business_id'
           AND c.udt_name = 'uuid'
           AND t.table_type = 'BASE TABLE'
         ORDER BY c.table_name
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO helvoca_runtime', table_row.table_name);
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_row.table_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', table_row.table_name);
        EXECUTE format('DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.%I', table_row.table_name);
        EXECUTE format(
            'CREATE POLICY helvoca_tenant_isolation ON public.%I TO PUBLIC '
            || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
            || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
            table_row.table_name,
            migration_owner,
            migration_owner
        );
    END LOOP;
END
$$;

-- BUSINESS is itself the tenant root and therefore has no business_id column.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business TO helvoca_runtime;
ALTER TABLE public.business ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS helvoca_business_isolation ON public.business;
DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_business_isolation ON public.business TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

-- Runtime code never needs Flyway history. Keep migration metadata owner-only.
REVOKE ALL ON TABLE public.flyway_schema_history FROM helvoca_runtime, helvoca_system;

COMMENT ON FUNCTION public.helvoca_rls_business_id() IS
    'Returns the tenant UUID installed by TenantAwareDataSource; NULL means no tenant scope.';
