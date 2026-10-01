-- V84 Live Demo session preparation
-- Platform-owned lifecycle around a prepared presentation. This table does not
-- contain provider credentials and does not itself authorize any external effect.

CREATE TABLE public.demo_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES public.demo_profile(id) ON DELETE RESTRICT,
    runtime_business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    state VARCHAR(20) NOT NULL,
    expected_participant_phone VARCHAR(30),
    configuration_revision VARCHAR(64),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prepared_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_demo_session_state
        CHECK (state IN ('PREPARING', 'READY', 'ACTIVE', 'FINISHED', 'FAILED')),
    CONSTRAINT ck_demo_session_participant_phone
        CHECK (expected_participant_phone IS NULL OR expected_participant_phone ~ '^\+[1-9][0-9]{7,14}$')
);

-- Exactly one session may be open at a time in V1. This closes the race between
-- two platform admins preparing different profiles concurrently.
CREATE UNIQUE INDEX uq_demo_session_single_open
    ON public.demo_session ((1))
    WHERE state IN ('PREPARING', 'READY', 'ACTIVE');

CREATE INDEX idx_demo_session_runtime_created
    ON public.demo_session(runtime_business_id, created_at DESC);

REVOKE ALL ON TABLE public.demo_session FROM PUBLIC, helvoca_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demo_session TO helvoca_system;

COMMENT ON TABLE public.demo_session IS
    'Platform-owned live demo lifecycle. READY/ACTIVE never imply outbound effects are armed.';
