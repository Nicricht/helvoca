-- V85: durable Live Demo inbound voice correlation.
-- Correlation is stored on call_session and protected at the database boundary so
-- a call can only point at a DemoSession owned by the exact same business/runtime.

ALTER TABLE public.demo_session
    ADD CONSTRAINT uq_demo_session_id_runtime UNIQUE (id, runtime_business_id);

ALTER TABLE public.call_session
    ADD COLUMN demo_session_id UUID;

ALTER TABLE public.call_session
    ADD CONSTRAINT fk_call_session_demo_session_runtime
        FOREIGN KEY (demo_session_id, business_id)
        REFERENCES public.demo_session(id, runtime_business_id)
        ON DELETE RESTRICT;

CREATE INDEX idx_call_session_demo_session_started
    ON public.call_session(demo_session_id, started_at ASC)
    WHERE demo_session_id IS NOT NULL;

COMMENT ON COLUMN public.call_session.demo_session_id IS
    'Durable correlation to the Live Demo session. Composite FK guarantees the call and demo runtime business are identical.';
