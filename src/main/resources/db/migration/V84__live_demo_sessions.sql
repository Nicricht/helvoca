-- V84: persistent Live Demo sessions.
-- A demo session is tenant-scoped to the dedicated runtime business. Platform
-- services may administer it through helvoca_system; tenant runtime users get
-- read-only visibility for their own tenant and can never use it cross-tenant.

CREATE TABLE public.demo_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    demo_profile_id UUID NOT NULL REFERENCES public.demo_profile(id) ON DELETE RESTRICT,
    runtime_business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE RESTRICT,
    state VARCHAR(20) NOT NULL,
    configuration_revision VARCHAR(120) NOT NULL,
    readiness_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_reason TEXT,
    staged_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_demo_session_state
        CHECK (state IN ('PREPARING', 'READY', 'ACTIVE', 'FINISHED', 'FAILED')),
    CONSTRAINT ck_demo_session_timestamps
        CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE INDEX idx_demo_session_runtime_created
    ON public.demo_session(runtime_business_id, created_at DESC);

CREATE INDEX idx_demo_session_profile_revision
    ON public.demo_session(demo_profile_id, configuration_revision, created_at DESC);

-- V1 deliberately allows only one session that can receive/live-correlate demo
-- traffic for a runtime. The database enforces this under concurrent Prepare calls.
CREATE UNIQUE INDEX uq_demo_session_one_live_runtime
    ON public.demo_session(runtime_business_id)
    WHERE state IN ('PREPARING', 'READY', 'ACTIVE');

REVOKE ALL ON TABLE public.demo_session FROM PUBLIC, helvoca_runtime, helvoca_system;
GRANT SELECT ON TABLE public.demo_session TO helvoca_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demo_session TO helvoca_system;

ALTER TABLE public.demo_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demo_session FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_demo_session_isolation ON public.demo_session;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_demo_session_isolation ON public.demo_session TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR runtime_business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR runtime_business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.demo_session IS
    'Tenant-scoped boundary for a prepared/live demo. PLATFORM/SYSTEM owns mutations; runtime tenants are read-only and isolated.';
COMMENT ON COLUMN public.demo_session.configuration_revision IS
    'Immutable profile revision identifier used for Prepare idempotency and audit.';
COMMENT ON COLUMN public.demo_session.correlation_id IS
    'Public-safe correlation identifier for later inbound call/operation timeline binding.';
