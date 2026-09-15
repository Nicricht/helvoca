-- V34 Outbound Messaging Engine
-- Durable, tenant-scoped preparation of authorized outbound messages.
-- Provider delivery remains independently guarded and disabled by default.

ALTER TABLE customer_identity
    ADD CONSTRAINT uq_customer_identity_id_business UNIQUE (id, business_id);

ALTER TABLE business_operation
    ADD CONSTRAINT uq_business_operation_id_business UNIQUE (id, business_id);

CREATE TABLE outbound_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    recipient_identity_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    recipient_address VARCHAR(180) NOT NULL,
    provider VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    idempotency_key VARCHAR(220) NOT NULL,
    content_text TEXT NOT NULL,
    provider_message_id VARCHAR(180),
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT fk_outbound_message_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customer(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_outbound_message_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_outbound_message_identity_tenant
        FOREIGN KEY (recipient_identity_id, business_id)
        REFERENCES customer_identity(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT uq_outbound_message_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT ck_outbound_message_channel CHECK (channel IN ('WHATSAPP')),
    CONSTRAINT ck_outbound_message_purpose CHECK (purpose IN (
        'PAYMENT_LINK','BOOKING_CONFIRMATION','MEETING_LINK','ORDER_STATUS',
        'QUOTE','REMINDER','DELIVERY_STATUS'
    )),
    CONSTRAINT ck_outbound_message_status CHECK (status IN (
        'PREPARED','QUEUED','SENT','FAILED','CANCELLED','BLOCKED'
    )),
    CONSTRAINT ck_outbound_message_terminal_timestamps CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND sent_at IS NULL)
        OR (status NOT IN ('SENT','CANCELLED') AND sent_at IS NULL AND cancelled_at IS NULL)
    )
);

CREATE INDEX idx_outbound_message_business_created
    ON outbound_message(business_id, created_at DESC);
CREATE INDEX idx_outbound_message_customer
    ON outbound_message(business_id, customer_id, created_at DESC);
CREATE INDEX idx_outbound_message_operation
    ON outbound_message(business_id, operation_id, created_at DESC);
