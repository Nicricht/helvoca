-- Keep existing rows unchanged. NULL source does NOT establish Twilio ownership.
ALTER TABLE phone_number
    ADD COLUMN IF NOT EXISTS twilio_binding_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS twilio_account_sid VARCHAR(34),
    ADD COLUMN IF NOT EXISTS voice_verified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_source
    CHECK (twilio_binding_source IS NULL OR twilio_binding_source = 'MANAGED_PURCHASE');

-- Explicit CASE avoids PostgreSQL CHECK's permissive UNKNOWN/NULL semantics.
ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_account
    CHECK (
        CASE
            WHEN twilio_binding_source IS NULL
                THEN twilio_account_sid IS NULL
            WHEN twilio_binding_source = 'MANAGED_PURCHASE'
                THEN twilio_account_sid IS NOT NULL
                     AND twilio_account_sid ~ '^AC[0-9a-fA-F]{32}$'
                     AND external_id IS NOT NULL
                     AND external_id ~ '^PN[0-9a-fA-F]{32}$'
            ELSE FALSE
        END
    );

ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_verified_requires_managed_binding
    CHECK (voice_verified_at IS NULL OR twilio_binding_source IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS uq_phone_managed_twilio_account_sid
    ON phone_number (twilio_account_sid, external_id)
    WHERE twilio_binding_source = 'MANAGED_PURCHASE';
