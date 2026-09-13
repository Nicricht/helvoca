ALTER TABLE call_session
    ADD COLUMN estimated_telephony_cost_usd NUMERIC(12,6),
    ADD COLUMN estimated_ai_cost_usd NUMERIC(12,6),
    ADD COLUMN estimated_total_cost_usd NUMERIC(12,6);

CREATE INDEX idx_call_session_business_status
    ON call_session(business_id, status);
