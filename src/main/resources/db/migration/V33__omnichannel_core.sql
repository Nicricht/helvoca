-- V33 Omnichannel Core
--
-- Omnichannel linking is intentionally conservative: only channel sessions
-- already attached to an explicit tenant-scoped customer can share state.
-- Raw phone similarity is never sufficient to merge two people.

ALTER TABLE customer
    ADD CONSTRAINT uq_customer_id_business UNIQUE (id, business_id);

CREATE TABLE customer_identity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    identity_type VARCHAR(20) NOT NULL,
    normalized_value VARCHAR(180) NOT NULL,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    source VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customer_identity_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customer(id, business_id) ON DELETE CASCADE,
    CONSTRAINT uq_customer_identity_customer_value
        UNIQUE (business_id, customer_id, identity_type, normalized_value),
    CONSTRAINT ck_customer_identity_type
        CHECK (identity_type IN ('PHONE')),
    CONSTRAINT ck_customer_identity_verification
        CHECK (verification_status IN ('UNVERIFIED','PROVIDER_ASSERTED','CUSTOMER_VERIFIED','MANUAL_VERIFIED')),
    CONSTRAINT ck_customer_identity_verified_at
        CHECK (
            (verification_status IN ('CUSTOMER_VERIFIED','MANUAL_VERIFIED') AND verified_at IS NOT NULL)
            OR
            (verification_status IN ('UNVERIFIED','PROVIDER_ASSERTED'))
        )
);

CREATE INDEX idx_customer_identity_lookup
    ON customer_identity(business_id, identity_type, normalized_value, verification_status);

-- Preserve existing declared phone numbers as observations only. They are NOT
-- trusted for automatic cross-channel linking until explicitly verified.
WITH normalized AS (
    SELECT
        business_id,
        id AS customer_id,
        '+' || regexp_replace(phone, '[^0-9]', '', 'g') AS phone_value
    FROM customer
    WHERE phone IS NOT NULL
      AND btrim(phone) LIKE '+%'
), valid AS (
    SELECT *
    FROM normalized
    WHERE phone_value ~ '^\+[0-9]{8,15}$'
)
INSERT INTO customer_identity (
    business_id, customer_id, identity_type, normalized_value,
    verification_status, source
)
SELECT business_id, customer_id, 'PHONE', phone_value, 'UNVERIFIED', 'LEGACY_CUSTOMER_FIELD'
FROM valid
ON CONFLICT (business_id, customer_id, identity_type, normalized_value) DO NOTHING;

CREATE TABLE omnichannel_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_omnichannel_session_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES customer(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT uq_omnichannel_session_id_business UNIQUE (id, business_id),
    CONSTRAINT ck_omnichannel_session_status CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT ck_omnichannel_session_revision CHECK (revision > 0),
    CONSTRAINT ck_omnichannel_session_closed_at CHECK (
        (status = 'ACTIVE' AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_omnichannel_active_customer
    ON omnichannel_session(business_id, customer_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_omnichannel_session_customer
    ON omnichannel_session(business_id, customer_id, last_activity_at DESC);

CREATE TABLE omnichannel_channel_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    omnichannel_session_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    source_reference_id UUID NOT NULL,
    normalized_address VARCHAR(180),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_omnichannel_channel_session_tenant
        FOREIGN KEY (omnichannel_session_id, business_id)
        REFERENCES omnichannel_session(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT uq_omnichannel_channel_source
        UNIQUE (business_id, channel, source_reference_id),
    CONSTRAINT ck_omnichannel_channel
        CHECK (channel IN ('VOICE','WHATSAPP','MANUAL','API'))
);

CREATE INDEX idx_omnichannel_channel_session_parent
    ON omnichannel_channel_session(business_id, omnichannel_session_id, last_activity_at DESC);

ALTER TABLE conversation_operation_state
    ADD COLUMN omnichannel_session_id UUID;

ALTER TABLE conversation_operation_state
    ADD CONSTRAINT fk_conversation_state_omnichannel_tenant
        FOREIGN KEY (omnichannel_session_id, business_id)
        REFERENCES omnichannel_session(id, business_id) ON DELETE RESTRICT;

-- Exactly one shared conversation state may belong to a tenant-scoped
-- omnichannel session. Anonymous legacy rows remain governed by the existing
-- channel/source unique constraint.
CREATE UNIQUE INDEX uq_conversation_state_omnichannel
    ON conversation_operation_state(business_id, omnichannel_session_id)
    WHERE omnichannel_session_id IS NOT NULL;
