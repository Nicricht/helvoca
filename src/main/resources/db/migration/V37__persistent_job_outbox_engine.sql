-- V37 Persistent Job / Outbox Engine
-- Durable, tenant-scoped work queue with leases, retries and dead-letter state.

CREATE TABLE persistent_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    operation_id UUID,
    job_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(220) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(500),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_persistent_job_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_persistent_job_idempotency
        UNIQUE (business_id, idempotency_key),
    CONSTRAINT ck_persistent_job_status CHECK (
        status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','DEAD_LETTER','CANCELLED')
    ),
    CONSTRAINT ck_persistent_job_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_persistent_job_max_attempts CHECK (max_attempts BETWEEN 1 AND 100),
    CONSTRAINT ck_persistent_job_attempt_bounds CHECK (attempt_count <= max_attempts),
    CONSTRAINT ck_persistent_job_lease CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR status <> 'RUNNING'
    )
);

CREATE INDEX idx_persistent_job_ready
    ON persistent_job(next_attempt_at, created_at)
    WHERE status IN ('PENDING','FAILED');

CREATE INDEX idx_persistent_job_stale_lease
    ON persistent_job(lease_expires_at)
    WHERE status = 'RUNNING';

CREATE INDEX idx_persistent_job_business_recent
    ON persistent_job(business_id, created_at DESC);

CREATE INDEX idx_persistent_job_operation
    ON persistent_job(business_id, operation_id, created_at DESC)
    WHERE operation_id IS NOT NULL;
