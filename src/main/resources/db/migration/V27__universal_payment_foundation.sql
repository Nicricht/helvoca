-- Universal PAYMENT is intentionally provider-agnostic. Existing Mercado Pago
-- billing credentials belong to Helvoca SaaS subscriptions and are not reused
-- for merchant/customer payments.

ALTER TABLE business_operation
    DROP CONSTRAINT ck_business_operation_type;

ALTER TABLE business_operation
    ADD CONSTRAINT ck_business_operation_type
        CHECK (type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING','PAYMENT'));

CREATE TABLE business_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES business_operation(id) ON DELETE RESTRICT,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    target_operation_id UUID NOT NULL REFERENCES business_operation(id) ON DELETE RESTRICT,
    contact_phone VARCHAR(30),
    provider VARCHAR(60) NOT NULL,
    external_id VARCHAR(180),
    idempotency_key VARCHAR(180) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    checkout_url TEXT,
    source VARCHAR(20) NOT NULL,
    metadata_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_payment_operation UNIQUE (operation_id),
    CONSTRAINT uq_business_payment_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT uq_business_payment_provider_external UNIQUE (business_id, provider, external_id),
    CONSTRAINT ck_business_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_business_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_business_payment_status CHECK (status IN (
        'REQUIRES_ACTION','PENDING','SUCCEEDED','FAILED','CANCELLED','EXPIRED','REFUNDED'
    )),
    CONSTRAINT ck_business_payment_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API'))
);

CREATE INDEX idx_business_payment_business_created
    ON business_payment(business_id, created_at DESC);
CREATE INDEX idx_business_payment_customer_created
    ON business_payment(business_id, customer_id, created_at DESC);
CREATE INDEX idx_business_payment_phone_created
    ON business_payment(business_id, contact_phone, created_at DESC);
CREATE INDEX idx_business_payment_source_reference
    ON business_payment(business_id, source_reference_id, created_at DESC);
CREATE INDEX idx_business_payment_target_operation
    ON business_payment(business_id, target_operation_id, created_at DESC);

-- PAYMENT is opt-in. No existing tenant receives payment capabilities in this
-- migration. A merchant payment provider adapter/configuration must be added
-- explicitly before enabling the capability for a tenant.
