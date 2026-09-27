-- Commercial observability V1
-- Minimal runtime metadata required to diagnose a single voice call without
-- introducing a parallel tracing store.

ALTER TABLE call_session
    ADD COLUMN ai_model VARCHAR(120);

ALTER TABLE call_action
    ADD COLUMN duration_ms BIGINT;

ALTER TABLE call_action
    ADD CONSTRAINT ck_call_action_duration_ms_non_negative
    CHECK (duration_ms IS NULL OR duration_ms >= 0);

COMMENT ON COLUMN call_session.ai_model IS
    'Exact configured voice AI model used for the call; diagnostic metadata only.';

COMMENT ON COLUMN call_action.duration_ms IS
    'Elapsed backend tool execution time in milliseconds; diagnostic metadata only.';
