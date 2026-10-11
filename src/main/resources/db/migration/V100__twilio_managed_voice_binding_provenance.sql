-- Preserve historical phone rows unmodified. NULL means NOT provider-certified.
-- Only trusted server-side provisioning may create MANAGED_PURCHASE bindings.
ALTER TABLE phone_number
    ADD COLUMN IF NOT EXISTS twilio_binding_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS twilio_account_sid VARCHAR(34),
    ADD COLUMN IF NOT EXISTS voice_verified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_source
    CHECK (twilio_binding_source IS NULL OR twilio_binding_source IN ('MANAGED_PURCHASE'));

ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_account
    CHECK (
        (twilio_binding_source IS NULL AND twilio_account_sid IS NULL)
        OR
        (twilio_binding_source = 'MANAGED_PURCHASE'
         AND twilio_account_sid ~ '^AC[0-9a-fA-F]{32}$'
         AND external_id ~ '^PN[0-9a-fA-F]{32}$')
    );

CREATE INDEX IF NOT EXISTS idx_phone_twilio_account_sid
    ON phone_number (twilio_account_sid, external_id)
    WHERE twilio_account_sid IS NOT NULL;
