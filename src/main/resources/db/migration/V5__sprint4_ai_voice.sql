CREATE TABLE call_summary (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL UNIQUE REFERENCES call_session(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    intent VARCHAR(100),
    outcome VARCHAR(100),
    sentiment VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_call_summary_call ON call_summary(call_id);
