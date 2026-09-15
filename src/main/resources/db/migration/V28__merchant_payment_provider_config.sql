CREATE TABLE business_payment_provider_config (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    provider VARCHAR(60) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    credential_ref VARCHAR(80) NOT NULL,
    webhook_key UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_payment_provider_webhook_key UNIQUE (webhook_key),
    CONSTRAINT ck_business_payment_provider_mode CHECK (mode IN ('SANDBOX','LIVE')),
    CONSTRAINT ck_business_payment_provider_credential_ref CHECK (credential_ref ~ '^[A-Z0-9_]{2,80}$')
);

CREATE TABLE payment_webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    provider VARCHAR(60) NOT NULL,
    event_id VARCHAR(180) NOT NULL,
    external_id VARCHAR(180),
    status VARCHAR(30) NOT NULL,
    payload_hash VARCHAR(64),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_payment_webhook_event UNIQUE (business_id, provider, event_id),
    CONSTRAINT ck_payment_webhook_event_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
);

CREATE INDEX idx_payment_webhook_event_external
    ON payment_webhook_event(business_id, provider, external_id);

-- V28 intentionally does not enable PAYMENT for any existing tenant and does
-- not store provider access tokens or webhook secrets. Merchant credentials are
-- resolved from deployment secrets through credential_ref.
