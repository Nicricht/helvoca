CREATE TABLE human_handoff (
    id UUID PRIMARY KEY,
    sequence_no BIGSERIAL NOT NULL UNIQUE,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    channel VARCHAR(20),
    source_reference_id UUID,
    operation_id UUID,
    operation_type VARCHAR(20) NOT NULL,
    tool_name VARCHAR(80) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    failure_class VARCHAR(40) NOT NULL,
    fallback_action VARCHAR(40) NOT NULL DEFAULT 'HUMAN_HANDOFF',
    retry_count INTEGER NOT NULL DEFAULT 0,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    safe_summary VARCHAR(500),
    assigned_to VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_human_handoff_channel
        CHECK (channel IS NULL OR channel IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_human_handoff_operation_type
        CHECK (operation_type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING','PAYMENT')),
    CONSTRAINT ck_human_handoff_failure_class
        CHECK (failure_class = 'UNRESOLVABLE'),
    CONSTRAINT ck_human_handoff_retry_count
        CHECK (retry_count BETWEEN 0 AND 5),
    CONSTRAINT ck_human_handoff_priority
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_human_handoff_status
        CHECK (status IN ('OPEN','ACKNOWLEDGED','ASSIGNED','RESOLVED','CANCELLED')),
    CONSTRAINT ck_human_handoff_summary_length
        CHECK (safe_summary IS NULL OR char_length(safe_summary) <= 500),
    CONSTRAINT ck_human_handoff_assignee_length
        CHECK (assigned_to IS NULL OR char_length(assigned_to) <= 160)
);

CREATE UNIQUE INDEX uq_human_handoff_active_dedupe
    ON human_handoff(
        business_id,
        COALESCE(source_reference_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(operation_id, '00000000-0000-0000-0000-000000000000'::uuid),
        operation_type,
        reason_code)
    WHERE status IN ('OPEN','ACKNOWLEDGED','ASSIGNED');

CREATE INDEX idx_human_handoff_business_status_sequence
    ON human_handoff(business_id, status, sequence_no DESC);
CREATE INDEX idx_human_handoff_business_source
    ON human_handoff(business_id, source_reference_id, sequence_no DESC);
CREATE INDEX idx_human_handoff_business_operation
    ON human_handoff(business_id, operation_id, sequence_no DESC);
CREATE INDEX idx_human_handoff_business_customer
    ON human_handoff(business_id, customer_id, sequence_no DESC);

CREATE TABLE human_handoff_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no BIGSERIAL NOT NULL UNIQUE,
    handoff_id UUID NOT NULL REFERENCES human_handoff(id) ON DELETE RESTRICT,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(160),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_human_handoff_event_type
        CHECK (event_type IN ('CREATED','ACKNOWLEDGED','ASSIGNED','RESOLVED','CANCELLED')),
    CONSTRAINT ck_human_handoff_event_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('OPEN','ACKNOWLEDGED','ASSIGNED','RESOLVED','CANCELLED')),
    CONSTRAINT ck_human_handoff_event_status
        CHECK (status IN ('OPEN','ACKNOWLEDGED','ASSIGNED','RESOLVED','CANCELLED')),
    CONSTRAINT ck_human_handoff_event_actor
        CHECK (actor_type IN ('AUTOMATION','BUSINESS_USER'))
);

CREATE INDEX idx_human_handoff_event_business_sequence
    ON human_handoff_event(business_id, sequence_no DESC);
CREATE INDEX idx_human_handoff_event_handoff_sequence
    ON human_handoff_event(handoff_id, sequence_no ASC);

CREATE OR REPLACE FUNCTION reject_human_handoff_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'human_handoff cannot be deleted';
END;
$$;

CREATE TRIGGER trg_human_handoff_no_delete
BEFORE DELETE ON human_handoff
FOR EACH ROW
EXECUTE FUNCTION reject_human_handoff_delete();

CREATE OR REPLACE FUNCTION reject_human_handoff_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'human_handoff_event is append-only';
END;
$$;

CREATE TRIGGER trg_human_handoff_event_immutable
BEFORE UPDATE OR DELETE ON human_handoff_event
FOR EACH ROW
EXECUTE FUNCTION reject_human_handoff_event_mutation();
