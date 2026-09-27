CREATE TABLE twilio_certification_command (
    run_id VARCHAR(128) PRIMARY KEY,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    callback_token VARCHAR(64) UNIQUE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    provider_call_sid VARCHAR(64),
    completed_at TIMESTAMPTZ,
    failure_reason VARCHAR(400),
    CONSTRAINT ck_twilio_certification_command_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'INGRESS_CONSUMED', 'FAILED'))
);

CREATE INDEX ix_twilio_certification_command_pending
    ON twilio_certification_command (requested_at)
    WHERE status = 'PENDING';
