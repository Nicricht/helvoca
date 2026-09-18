-- V47 Tenant RLS for incident campaign tables
-- V44 had already been applied in production, so hardening added later belongs
-- in a new migration rather than changing the checksum of V44.

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.booking_incident_campaign TO helvoca_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.booking_incident_recipient TO helvoca_runtime;

ALTER TABLE public.booking_incident_campaign ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_campaign FORCE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_recipient ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_recipient FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.booking_incident_campaign;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.booking_incident_recipient;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.booking_incident_campaign TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.booking_incident_recipient TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;
