CREATE TABLE team_invitation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(180) NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_team_invitation_role CHECK (role_code IN ('BUSINESS_ADMIN','OPERATOR'))
);

CREATE INDEX idx_team_invitation_business_created
    ON team_invitation (business_id, created_at DESC);
CREATE INDEX idx_team_invitation_business_email
    ON team_invitation (business_id, lower(email));

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.team_invitation
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.team_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_invitation FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.team_invitation;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.team_invitation TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;
