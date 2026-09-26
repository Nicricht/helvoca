CREATE TABLE pilot_launch_control (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    responsible_name VARCHAR(180),
    responsible_contact VARCHAR(180),
    goal TEXT,
    planned_end_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pilot_launch_status
        CHECK (status IN ('DRAFT','READY','RUNNING','PAUSED','COMPLETED'))
);


GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.pilot_launch_control
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.pilot_launch_control ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pilot_launch_control FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.pilot_launch_control;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.pilot_launch_control TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.pilot_launch_control IS
    'Tenant-scoped lifecycle control for assisted business pilots.';
