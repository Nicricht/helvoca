-- Operator-controlled onboarding for genuine, independent platform administrators.
-- This migration creates no users, privileges, invitations or live side effects.
CREATE TABLE public.platform_admin_invitation (
    invitation_id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(180) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('BOOTSTRAP', 'INVITE')),
    issued_by UUID REFERENCES public.app_user(id),
    accepted_user_id UUID REFERENCES public.app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_platform_inviter CHECK
       ((purpose = 'BOOTSTRAP' AND issued_by IS NULL)
        OR (purpose = 'INVITE' AND issued_by IS NOT NULL)),
    CONSTRAINT chk_platform_terminal CHECK
       (accepted_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT chk_platform_accepted_user CHECK
       ((accepted_at IS NULL) = (accepted_user_id IS NULL))
);

CREATE UNIQUE INDEX uq_platform_admin_bootstrap_pending
    ON public.platform_admin_invitation (purpose)
    WHERE purpose = 'BOOTSTRAP'
      AND accepted_at IS NULL AND revoked_at IS NULL;

CREATE UNIQUE INDEX uq_platform_admin_invitation_pending_email
    ON public.platform_admin_invitation (lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

REVOKE ALL ON TABLE public.platform_admin_invitation FROM PUBLIC, helvoca_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.platform_admin_invitation TO helvoca_system;
ALTER TABLE public.platform_admin_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.platform_admin_invitation FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_platform_admin_invitation
    ON public.platform_admin_invitation
    FOR ALL TO helvoca_system
    USING (true) WITH CHECK (true);
