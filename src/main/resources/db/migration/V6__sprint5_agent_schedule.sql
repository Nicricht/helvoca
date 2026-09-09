CREATE TABLE ai_agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL UNIQUE REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    language VARCHAR(10) NOT NULL,
    voice VARCHAR(100) NOT NULL,
    greeting TEXT NOT NULL,
    instructions TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_agent_capability (
    ai_agent_id UUID NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    capability VARCHAR(80) NOT NULL,
    PRIMARY KEY (ai_agent_id, capability)
);

CREATE TABLE business_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hours_range CHECK (open_time < close_time)
);

CREATE INDEX idx_business_hours_business_day
    ON business_hours(business_id, day_of_week, open_time);

CREATE TABLE business_schedule_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT TRUE,
    open_time TIME,
    close_time TIME,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_schedule_exception_date UNIQUE (business_id, exception_date),
    CONSTRAINT ck_business_schedule_exception_hours CHECK (
        (closed = TRUE AND open_time IS NULL AND close_time IS NULL)
        OR
        (closed = FALSE AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    )
);

CREATE INDEX idx_business_schedule_exception_business_date
    ON business_schedule_exception(business_id, exception_date);

-- Existing tenants receive a working default agent so Sprint 4 behavior remains compatible.
INSERT INTO ai_agent (id, business_id, name, language, voice, greeting, instructions, active)
SELECT gen_random_uuid(), b.id, 'Helvoca', b.language, 'marin',
       'Hola, gracias por llamar a ' || b.name || '. ¿En qué puedo ayudarte?',
       NULL, TRUE
FROM business b;

INSERT INTO ai_agent_capability (ai_agent_id, capability)
SELECT a.id, capability
FROM ai_agent a
CROSS JOIN (VALUES
    ('GET_BUSINESS_INFORMATION'),
    ('LIST_SERVICES'),
    ('SEARCH_KNOWLEDGE'),
    ('FIND_CALLER'),
    ('REGISTER_CALLER'),
    ('CHECK_BOOKING_AVAILABILITY'),
    ('CREATE_BOOKING')
) AS capabilities(capability);
