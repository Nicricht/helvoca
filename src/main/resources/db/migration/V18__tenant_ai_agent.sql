CREATE TABLE ai_agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL UNIQUE REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'es',
    voice VARCHAR(100),
    greeting TEXT NOT NULL,
    instructions TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_agent_capability (
    ai_agent_id UUID NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    capability VARCHAR(80) NOT NULL,
    PRIMARY KEY (ai_agent_id, capability)
);

CREATE INDEX idx_ai_agent_business ON ai_agent(business_id);
