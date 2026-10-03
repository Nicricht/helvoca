-- V94 Public self-service booking portal
--
-- Public booking uses an opaque key that resolves to a tenant only on the
-- server. Existing business profiles receive a key but remain disabled.
-- Idempotency records are tenant-owned and protected by the standard RLS
-- contract so a replay cannot cross business boundaries.

ALTER TABLE public.business_profile
    ADD COLUMN public_booking_key UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN public_booking_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_business_profile_public_booking_key
    ON public.business_profile(public_booking_key);

CREATE TABLE public.public_booking_submission (
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    booking_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT fk_public_booking_submission_booking_tenant
        FOREIGN KEY (booking_id, business_id)
        REFERENCES public.booking(id, business_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_public_booking_submission_key
        CHECK (length(btrim(idempotency_key)) BETWEEN 8 AND 128),
    CONSTRAINT ck_public_booking_submission_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_public_booking_submission_booking
    ON public.public_booking_submission(business_id, booking_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.public_booking_submission
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.public_booking_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.public_booking_submission FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.public_booking_submission TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;
