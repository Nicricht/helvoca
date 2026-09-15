CREATE TABLE business_operation_retry_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no BIGSERIAL NOT NULL UNIQUE,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    operation_type VARCHAR(20) NOT NULL,
    source_reference_id UUID,
    operation_id UUID,
    tool_name VARCHAR(80) NOT NULL,
    attempt_no INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    failure_class VARCHAR(40),
    error_code VARCHAR(80),
    delay_ms INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_operation_retry_type
        CHECK (operation_type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING','PAYMENT')),
    CONSTRAINT ck_business_operation_retry_attempt_no
        CHECK (attempt_no BETWEEN 1 AND 6),
    CONSTRAINT ck_business_operation_retry_max_attempts
        CHECK (max_attempts BETWEEN 1 AND 6 AND attempt_no <= max_attempts),
    CONSTRAINT ck_business_operation_retry_outcome
        CHECK (outcome IN ('RETRY_SCHEDULED','SUCCEEDED_AFTER_RETRY','RETRIES_EXHAUSTED','FALLBACK_APPLIED','UNRESOLVABLE')),
    CONSTRAINT ck_business_operation_retry_failure_class
        CHECK (failure_class IS NULL OR failure_class IN ('TRANSIENT','RESOLVABLE_WITH_FALLBACK','UNRESOLVABLE')),
    CONSTRAINT ck_business_operation_retry_delay
        CHECK (delay_ms BETWEEN 0 AND 5000)
);

CREATE INDEX idx_business_operation_retry_business_sequence
    ON business_operation_retry_attempt(business_id, sequence_no DESC);
CREATE INDEX idx_business_operation_retry_source_sequence
    ON business_operation_retry_attempt(business_id, source_reference_id, sequence_no DESC);
CREATE INDEX idx_business_operation_retry_operation_sequence
    ON business_operation_retry_attempt(business_id, operation_id, sequence_no DESC);

CREATE OR REPLACE FUNCTION reject_business_operation_retry_attempt_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'business_operation_retry_attempt is append-only';
END;
$$;

CREATE TRIGGER trg_business_operation_retry_attempt_immutable
BEFORE UPDATE OR DELETE ON business_operation_retry_attempt
FOR EACH ROW
EXECUTE FUNCTION reject_business_operation_retry_attempt_mutation();
