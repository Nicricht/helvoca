-- V60: Minimal tenant-scoped legal hold foundation.
--
-- Holds are intentionally categorical: no free-text reason or copied customer
-- payload is stored here. Application runtime can only read holds in this slice;
-- mutation will require a separate BUSINESS_ADMIN-controlled path.

CREATE TABLE public.retention_legal_hold (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_retention_legal_hold_target_type CHECK (
        target_type IN (
            'CUSTOMER',
            'CALL_SESSION',
            'MESSAGING_CONVERSATION',
            'BUSINESS_OPERATION',
            'AUDIT_LOG',
            'OUTBOUND_MESSAGE'
        )
    ),
    CONSTRAINT ck_retention_legal_hold_reason CHECK (
        reason_code IN (
            'LEGAL_REQUEST',
            'PAYMENT_DISPUTE',
            'FRAUD_SECURITY',
            'CONTRACTUAL',
            'OTHER_DOCUMENTED'
        )
    ),
    CONSTRAINT ck_retention_legal_hold_actor_type CHECK (
        actor_type IN ('HUMAN', 'SYSTEM')
    ),
    CONSTRAINT ck_retention_legal_hold_actor_identity CHECK (
        (actor_type = 'HUMAN' AND actor_user_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL)
    ),
    CONSTRAINT ck_retention_legal_hold_release CHECK (
        released_at IS NULL OR released_at >= created_at
    )
);

CREATE UNIQUE INDEX uq_retention_legal_hold_active_target
    ON public.retention_legal_hold(business_id, target_type, target_id)
    WHERE released_at IS NULL;

CREATE INDEX idx_retention_legal_hold_active_lookup
    ON public.retention_legal_hold(business_id, target_type, target_id, created_at DESC)
    WHERE released_at IS NULL;

GRANT SELECT ON TABLE public.retention_legal_hold
    TO helvoca_runtime, helvoca_system;
REVOKE INSERT, UPDATE, DELETE ON TABLE public.retention_legal_hold
    FROM PUBLIC, helvoca_runtime, helvoca_system;

ALTER TABLE public.retention_legal_hold ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.retention_legal_hold FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.retention_legal_hold;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.retention_legal_hold TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.retention_legal_hold IS
    'Tenant-scoped categorical retention holds. No free-text customer data, credentials or secrets are permitted.';
