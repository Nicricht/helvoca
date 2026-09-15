-- Extend the universal operation envelope to QUOTE, LEAD and REQUEST while
-- preserving the existing typed tables as compatible projections.

ALTER TABLE business_quote
    ADD COLUMN operation_id UUID DEFAULT gen_random_uuid();

ALTER TABLE business_lead
    ADD COLUMN operation_id UUID DEFAULT gen_random_uuid();

ALTER TABLE business_request
    ADD COLUMN operation_id UUID DEFAULT gen_random_uuid();

INSERT INTO business_operation (
    id, business_id, customer_id, source_reference_id, type, status, source,
    revision, confirmation_token, contact_name, contact_phone,
    subtotal, delivery_fee, total, currency, metadata_json,
    created_at, updated_at
)
SELECT
    q.operation_id,
    q.business_id,
    q.customer_id,
    q.source_reference_id,
    'QUOTE',
    CASE WHEN q.status IN ('REJECTED', 'CANCELLED') THEN 'CANCELLED' ELSE 'CONFIRMED' END,
    q.source,
    1,
    NULL,
    q.contact_name,
    q.contact_phone,
    q.amount,
    0,
    q.amount,
    q.currency,
    jsonb_strip_nulls(jsonb_build_object(
        'intent', 'QUOTE',
        'title', q.title,
        'description', q.description,
        'projectionStatus', q.status,
        'migrated', true
    )),
    q.created_at,
    q.updated_at
FROM business_quote q;

INSERT INTO business_operation (
    id, business_id, customer_id, source_reference_id, type, status, source,
    revision, confirmation_token, contact_name, contact_phone,
    currency, metadata_json, created_at, updated_at
)
SELECT
    l.operation_id,
    l.business_id,
    l.customer_id,
    l.source_reference_id,
    'LEAD',
    'CONFIRMED',
    l.source,
    1,
    NULL,
    l.name,
    l.phone,
    'CLP',
    jsonb_strip_nulls(jsonb_build_object(
        'intent', 'LEAD',
        'name', l.name,
        'email', l.email,
        'interest', l.interest,
        'budget', l.budget,
        'notes', l.notes,
        'projectionStatus', l.status,
        'migrated', true
    )),
    l.created_at,
    l.updated_at
FROM business_lead l;

INSERT INTO business_operation (
    id, business_id, customer_id, source_reference_id, type, status, source,
    revision, confirmation_token, contact_name, contact_phone,
    currency, metadata_json, created_at, updated_at
)
SELECT
    r.operation_id,
    r.business_id,
    r.customer_id,
    r.call_id,
    'REQUEST',
    CASE WHEN r.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
    CASE r.source
        WHEN 'AI_CALL' THEN 'VOICE'
        WHEN 'AI_WHATSAPP' THEN 'WHATSAPP'
        WHEN 'MANUAL' THEN 'MANUAL'
        ELSE 'API'
    END,
    1,
    NULL,
    r.contact_name,
    r.contact_phone,
    'CLP',
    jsonb_strip_nulls(jsonb_build_object(
        'intent', 'REQUEST',
        'requestType', r.request_type,
        'title', r.title,
        'description', r.description,
        'priority', r.priority,
        'details', r.details_json,
        'projectionStatus', r.status,
        'migrated', true
    )),
    r.created_at,
    r.updated_at
FROM business_request r;

ALTER TABLE business_quote
    ALTER COLUMN operation_id DROP DEFAULT,
    ALTER COLUMN operation_id SET NOT NULL,
    ADD CONSTRAINT fk_business_quote_operation
        FOREIGN KEY (operation_id) REFERENCES business_operation(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_business_quote_operation UNIQUE (operation_id);

ALTER TABLE business_lead
    ALTER COLUMN operation_id DROP DEFAULT,
    ALTER COLUMN operation_id SET NOT NULL,
    ADD CONSTRAINT fk_business_lead_operation
        FOREIGN KEY (operation_id) REFERENCES business_operation(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_business_lead_operation UNIQUE (operation_id);

ALTER TABLE business_request
    ALTER COLUMN operation_id DROP DEFAULT,
    ALTER COLUMN operation_id SET NOT NULL,
    ADD CONSTRAINT fk_business_request_operation
        FOREIGN KEY (operation_id) REFERENCES business_operation(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_business_request_operation UNIQUE (operation_id);

CREATE INDEX idx_business_quote_operation ON business_quote(operation_id);
CREATE INDEX idx_business_lead_operation ON business_lead(operation_id);
CREATE INDEX idx_business_request_operation ON business_request(operation_id);

-- Structured conversation state is deliberately channel-agnostic. The latest
-- value for a key replaces the previous conflicting value, while revision
-- provides optimistic ordering and traceability for conversation corrections.
CREATE TABLE conversation_operation_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    source_reference_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    active_operation_id UUID REFERENCES business_operation(id) ON DELETE SET NULL,
    state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversation_operation_state UNIQUE (business_id, channel, source_reference_id),
    CONSTRAINT ck_conversation_operation_state_channel CHECK (channel IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_conversation_operation_state_revision CHECK (revision > 0),
    CONSTRAINT ck_conversation_operation_state_json CHECK (jsonb_typeof(state_json) = 'object')
);

CREATE INDEX idx_conversation_operation_state_active
    ON conversation_operation_state(business_id, active_operation_id);
