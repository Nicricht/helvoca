-- Separate, tenant-owned credentials recovery ledger.
-- Historical business accounts are grandfathered as email-verified.
-- Platform administrator V106/V107 invitation semantics are unchanged.
ALTER TABLE public.app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE public.app_user
   SET email_verified_at = clock_timestamp()
 WHERE business_id IS NOT NULL;

ALTER TABLE public.app_user
    ADD CONSTRAINT uq_app_user_id_business UNIQUE (id, business_id);

CREATE TABLE public.business_account_token (
    token_id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(8) NOT NULL CHECK (purpose IN ('RESET', 'VERIFY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_account_token_exact_user
        FOREIGN KEY (user_id, business_id)
        REFERENCES public.app_user(id, business_id) ON DELETE CASCADE,
    CONSTRAINT chk_account_token_terminal
        CHECK (consumed_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT chk_account_token_expiry
        CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX uq_business_account_token_pending
    ON public.business_account_token(user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_business_account_token_tenant
    ON public.business_account_token(business_id, user_id);

REVOKE ALL ON TABLE public.business_account_token FROM PUBLIC, helvoca_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.business_account_token TO helvoca_system;
ALTER TABLE public.business_account_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_account_token FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_business_account_token
    ON public.business_account_token FOR ALL TO helvoca_system
    USING (true) WITH CHECK (true);
