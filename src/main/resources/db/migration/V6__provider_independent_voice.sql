ALTER TABLE call_session
    ADD COLUMN telephony_provider VARCHAR(30) NOT NULL DEFAULT 'twilio';

ALTER TABLE call_session
    ADD COLUMN ai_provider VARCHAR(30);

CREATE INDEX idx_call_session_business_telephony_provider
    ON call_session (business_id, telephony_provider);

CREATE INDEX idx_call_session_business_ai_provider
    ON call_session (business_id, ai_provider);
