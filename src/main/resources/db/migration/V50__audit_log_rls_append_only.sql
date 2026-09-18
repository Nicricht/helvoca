-- V50: Harden audit_log as a tenant-scoped, application append-only ledger.
--
-- V40 already installed forced RLS for audit_log because it has business_id.
-- This migration makes that contract explicit and removes mutation privileges
-- from both application runtime roles while preserving owner/Flyway maintenance.

GRANT SELECT, INSERT ON TABLE public.audit_log TO helvoca_runtime, helvoca_system;
REVOKE UPDATE, DELETE ON TABLE public.audit_log FROM PUBLIC, helvoca_runtime, helvoca_system;

ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_log FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.audit_log;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.audit_log TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.audit_log IS
    'Tenant-scoped audit ledger. Application roles may SELECT/INSERT only; UPDATE/DELETE remain owner-maintenance operations.';
