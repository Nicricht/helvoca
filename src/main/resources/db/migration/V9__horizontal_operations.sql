CREATE TABLE business_capability (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_capability UNIQUE (business_id, code)
);

CREATE INDEX idx_business_capability_business ON business_capability(business_id);

CREATE TABLE business_request (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    call_id UUID REFERENCES call_session(id) ON DELETE SET NULL,
    category VARCHAR(100),
    subject VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    source VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_business_request_business_status ON business_request(business_id, status, created_at DESC);
CREATE INDEX idx_business_request_customer ON business_request(business_id, customer_id);

CREATE TABLE unanswered_question (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    call_id UUID REFERENCES call_session(id) ON DELETE SET NULL,
    question_key VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    occurrences INTEGER NOT NULL DEFAULT 1 CHECK (occurrences > 0),
    first_asked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_asked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unanswered_question_key UNIQUE (business_id, question_key)
);

CREATE INDEX idx_unanswered_question_business_status ON unanswered_question(business_id, status, last_asked_at DESC);

CREATE TABLE call_tool_event (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    call_id UUID NOT NULL REFERENCES call_session(id) ON DELETE CASCADE,
    tool_name VARCHAR(80) NOT NULL,
    success BOOLEAN NOT NULL,
    result_code VARCHAR(80),
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_call_tool_event_business_created ON call_tool_event(business_id, created_at DESC);
CREATE INDEX idx_call_tool_event_call ON call_tool_event(call_id, created_at);
