-- V61: Protect held audit rows from tenant-scoped retention purge.
--
-- audit_log remains append-only for normal application SQL. Runtime DELETE
-- stays revoked. The narrow SECURITY DEFINER retention function now excludes
-- rows protected by an active AUDIT_LOG legal hold for the same tenant.

CREATE OR REPLACE FUNCTION public.purge_expired_audit_log_for_current_tenant()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    tenant_id uuid;
    deleted_count integer;
BEGIN
    tenant_id := public.helvoca_rls_business_id();

    IF tenant_id IS NULL THEN
        RAISE EXCEPTION 'tenant context required';
    END IF;

    DELETE FROM public.audit_log a
     WHERE a.business_id = tenant_id
       AND a.created_at < NOW() - INTERVAL '24 months'
       AND NOT EXISTS (
           SELECT 1
           FROM public.retention_legal_hold h
           WHERE h.business_id = a.business_id
             AND h.target_type = 'AUDIT_LOG'
             AND h.target_id = a.id
             AND h.released_at IS NULL
       );

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END
$$;

REVOKE ALL ON FUNCTION public.purge_expired_audit_log_for_current_tenant()
    FROM PUBLIC, helvoca_system;
GRANT EXECUTE ON FUNCTION public.purge_expired_audit_log_for_current_tenant()
    TO helvoca_runtime;

COMMENT ON FUNCTION public.purge_expired_audit_log_for_current_tenant() IS
    'Deletes audit_log rows older than 24 months for the active tenant unless protected by an active AUDIT_LOG legal hold.';
