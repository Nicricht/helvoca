ALTER TABLE call_session
    ADD COLUMN certification BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_setup_completed_at TIMESTAMPTZ;

CREATE INDEX idx_call_session_certification_started
    ON call_session(certification, started_at DESC)
    WHERE certification = TRUE;
