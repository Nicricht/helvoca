-- Operator-controlled recovery for an existing platform administrator.
-- No account, role, token, or password is created by this migration.
ALTER TABLE public.platform_admin_invitation
    DROP CONSTRAINT platform_admin_invitation_purpose_check;

ALTER TABLE public.platform_admin_invitation
    DROP CONSTRAINT chk_platform_inviter;

ALTER TABLE public.platform_admin_invitation
    ADD CONSTRAINT chk_platform_admin_invitation_purpose
        CHECK (purpose IN ('BOOTSTRAP', 'INVITE', 'RECOVERY'));

ALTER TABLE public.platform_admin_invitation
    ADD CONSTRAINT chk_platform_inviter
        CHECK (((purpose IN ('BOOTSTRAP', 'RECOVERY')) AND issued_by IS NULL)
            OR (purpose = 'INVITE' AND issued_by IS NOT NULL));

