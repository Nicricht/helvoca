-- V51: Narrow retention maintenance path for audit_log.
--
-- audit_log remains append-only for normal application SQL. Runtime DELETE
-- stays revoked. The only mutation path exposed to a tenant is this
-- SECURITY DEFINER function, which:
--   * requires an active tenant context,
--   * derives the tenant from app.tenant_id,
--   * uses the fixed V1 24-month retention window,
--   * accepts no caller-supplied business_id or cutoff.

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

    DELETE FROM public.audit_log
     WHERE business_id = tenant_id
       AND created_at < NOW() - INTERVAL '24 months';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END
$$;

REVOKE ALL ON FUNCTION public.purge_expired_audit_log_for_current_tenant()
    FROM PUBLIC, helvoca_system;
GRANT EXECUTE ON FUNCTION public.purge_expired_audit_log_for_current_tenant()
    TO helvoca_runtime;

COMMENT ON FUNCTION public.purge_expired_audit_log_for_current_tenant() IS
    'Deletes only audit_log rows older than 24 months for the active tenant. No caller-supplied tenant or cutoff is accepted.';
