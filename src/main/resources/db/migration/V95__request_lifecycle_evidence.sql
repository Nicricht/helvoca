-- V95: durable, tenant-scoped lifecycle evidence for business requests.
-- Human transitions and later verified automated transitions share one ledger.
-- The existing operation event's actor inference is NOT used as human proof.
ALTER TABLE public.business_request
    ADD CONSTRAINT uq_business_request_id_business UNIQUE (id, business_id);

CREATE TABLE public.business_request_transition_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_reference VARCHAR(160),
    reason_code VARCHAR(80) NOT NULL,
    evidence_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_request_transition_request_tenant
        FOREIGN KEY (request_id, business_id)
        REFERENCES public.business_request(id, business_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_request_transition_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_request_transition_previous
        CHECK (previous_status IN ('OPEN','IN_PROGRESS','RESOLVED','CANCELLED')),
    CONSTRAINT ck_request_transition_status
        CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CANCELLED')),
    CONSTRAINT ck_request_transition_actor
        CHECK (actor_type IN ('BUSINESS_USER','AUTOMATION')),
    CONSTRAINT ck_request_transition_real_change
        CHECK (status <> previous_status),
    CONSTRAINT ck_request_transition_reason_length
        CHECK (char_length(reason_code) BETWEEN 1 AND 80)
);

CREATE INDEX idx_request_transition_tenant_request
    ON public.business_request_transition_event(business_id, request_id, created_at DESC);
CREATE UNIQUE INDEX uq_request_transition_automatic_evidence
    ON public.business_request_transition_event(business_id, request_id, evidence_event_id)
    WHERE evidence_event_id IS NOT NULL;

-- Existing retention can cascade-delete a request and its lifecycle records.
-- While a request exists, runtime can only append new lifecycle records.
GRANT SELECT, INSERT ON TABLE public.business_request_transition_event
    TO helvoca_runtime, helvoca_system;
REVOKE UPDATE, DELETE ON TABLE public.business_request_transition_event
    FROM PUBLIC, helvoca_runtime, helvoca_system;

ALTER TABLE public.business_request_transition_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_request_transition_event FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_request_transition_event TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
