CREATE TABLE twilio_certification_run (
    run_id VARCHAR(128) PRIMARY KEY,
    direction VARCHAR(32) NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
