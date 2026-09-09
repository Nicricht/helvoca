CREATE TABLE phone_number (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL DEFAULT 'TWILIO',
    external_id VARCHAR(100),
    phone_number VARCHAR(30) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_phone_number_id_business UNIQUE (id, business_id)
);

CREATE INDEX idx_phone_number_business_active ON phone_number(business_id, active);
CREATE UNIQUE INDEX uq_phone_number_provider_external
    ON phone_number(provider, external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE call_session (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    phone_number_id UUID NOT NULL,
    provider_call_id VARCHAR(100) NOT NULL UNIQUE,
    caller_number VARCHAR(30),
    destination_number VARCHAR(30) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    resolution VARCHAR(80),
    stream_sid VARCHAR(100) UNIQUE,
    stream_started_at TIMESTAMPTZ,
    stream_ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_call_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT fk_call_phone_tenant FOREIGN KEY (phone_number_id, business_id)
        REFERENCES phone_number(id, business_id)
);

CREATE INDEX idx_call_session_business_started ON call_session(business_id, started_at DESC);
CREATE INDEX idx_call_session_customer ON call_session(business_id, customer_id);
CREATE INDEX idx_call_session_status ON call_session(business_id, status);

CREATE TABLE call_transcript (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_session(id) ON DELETE CASCADE,
    speaker VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_call_transcript_sequence UNIQUE (call_id, sequence_number)
);

CREATE INDEX idx_call_transcript_call_sequence ON call_transcript(call_id, sequence_number);
