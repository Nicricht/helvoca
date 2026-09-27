-- V74: Self-Healing / Reconciliation Engine V6
-- Durable audit for tenant-scoped anomaly repairs. Detection itself remains read-only.

CREATE TABLE reconciliation_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    anomaly_type VARCHAR(80) NOT NULL,
    subject_type VARCHAR(60) NOT NULL,
    subject_id UUID NOT NULL,
    operation_id UUID,
    action VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(220) NOT NULL,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(500),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reconciliation_action_idempotency
        UNIQUE (business_id, idempotency_key),
    CONSTRAINT ck_reconciliation_action_status
        CHECK (status IN ('STARTED','COMPLETED','FAILED'))
);

CREATE INDEX idx_reconciliation_action_business_created
    ON reconciliation_action (business_id, created_at DESC);

CREATE INDEX idx_reconciliation_action_subject
    ON reconciliation_action (business_id, anomaly_type, subject_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.reconciliation_action
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.reconciliation_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reconciliation_action FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.reconciliation_action;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.reconciliation_action TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
