CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE business (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    legal_name VARCHAR(180),
    email VARCHAR(180),
    phone VARCHAR(30),
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Santiago',
    language VARCHAR(10) NOT NULL DEFAULT 'es',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE role_permission (
    role_id UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE phone_number (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(150),
    phone_number VARCHAR(30) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'es',
    voice VARCHAR(100),
    greeting TEXT,
    system_instructions TEXT NOT NULL,
    model VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_agent_capability (
    ai_agent_id UUID NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    capability VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (ai_agent_id, capability)
);

CREATE TABLE business_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    period_order SMALLINT NOT NULL DEFAULT 1 CHECK (period_order > 0),
    open_time TIME,
    close_time TIME,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_business_hours_times CHECK (
        (closed = TRUE AND open_time IS NULL AND close_time IS NULL)
        OR
        (closed = FALSE AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    ),
    UNIQUE (business_id, day_of_week, period_order)
);

CREATE TABLE business_schedule_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    open_time TIME,
    close_time TIME,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(200),
    CONSTRAINT chk_schedule_exception_times CHECK (
        (closed = TRUE AND open_time IS NULL AND close_time IS NULL)
        OR
        (closed = FALSE AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    ),
    UNIQUE (business_id, exception_date)
);

CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(180),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customer_business_phone ON customer(business_id, phone);

CREATE TABLE business_service (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    price NUMERIC(12,2) CHECK (price IS NULL OR price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE business_resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    resource_type VARCHAR(30) NOT NULL
        CHECK (resource_type IN ('STAFF','ROOM','TABLE','VEHICLE','EQUIPMENT','OTHER')),
    capacity INTEGER NOT NULL DEFAULT 1 CHECK (capacity > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE service_resource (
    service_id UUID NOT NULL REFERENCES business_service(id) ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES business_resource(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, resource_id)
);

CREATE TABLE booking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customer(id),
    service_id UUID REFERENCES business_service(id),
    resource_id UUID REFERENCES business_resource(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    party_size INTEGER NOT NULL DEFAULT 1 CHECK (party_size > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED'
        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
    source VARCHAR(30) NOT NULL DEFAULT 'AI_CALL'
        CHECK (source IN ('AI_CALL','WEB','ADMIN','IMPORT','API')),
    notes TEXT,
    idempotency_key VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_range CHECK (start_at < end_at)
);
CREATE INDEX idx_booking_business_start ON booking(business_id, start_at);
CREATE UNIQUE INDEX uq_booking_idempotency
    ON booking(business_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE booking
ADD CONSTRAINT booking_no_resource_overlap
EXCLUDE USING gist (
    business_id WITH =,
    resource_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (resource_id IS NOT NULL AND status IN ('PENDING','CONFIRMED'));

CREATE TABLE call_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id),
    phone_number_id UUID REFERENCES phone_number(id),
    ai_agent_id UUID REFERENCES ai_agent(id),
    provider VARCHAR(50) NOT NULL,
    provider_call_id VARCHAR(180),
    caller_number VARCHAR(30),
    destination_number VARCHAR(30),
    direction VARCHAR(20) NOT NULL DEFAULT 'INBOUND'
        CHECK (direction IN ('INBOUND','OUTBOUND')),
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('RINGING','IN_PROGRESS','COMPLETED','FAILED','TRANSFERRED','CANCELLED')),
    started_at TIMESTAMPTZ,
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    resolution VARCHAR(30),
    recording_uri TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_call_provider_id
    ON call_session(provider, provider_call_id)
    WHERE provider_call_id IS NOT NULL;
CREATE INDEX idx_call_business_created ON call_session(business_id, created_at DESC);

CREATE TABLE call_transcript (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES call_session(id) ON DELETE CASCADE,
    speaker VARCHAR(30) NOT NULL
        CHECK (speaker IN ('CUSTOMER','AI','OPERATOR','SYSTEM')),
    content TEXT NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    start_ms INTEGER CHECK (start_ms IS NULL OR start_ms >= 0),
    end_ms INTEGER CHECK (end_ms IS NULL OR end_ms >= 0),
    confidence NUMERIC(5,4) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (call_id, sequence_number)
);

CREATE TABLE call_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL UNIQUE REFERENCES call_session(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    intent VARCHAR(100),
    outcome VARCHAR(100),
    sentiment VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE human_transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL UNIQUE REFERENCES call_session(id) ON DELETE CASCADE,
    operator_user_id UUID REFERENCES app_user(id),
    reason VARCHAR(200),
    context TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED','ACCEPTED','COMPLETED','REJECTED','UNAVAILABLE')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE callback_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    call_id UUID REFERENCES call_session(id) ON DELETE SET NULL,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    phone VARCHAR(30) NOT NULL,
    reason VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONTACTED','COMPLETED','CANCELLED')),
    scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_business_active ON knowledge_item(business_id, active);

CREATE TABLE integration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    secret_reference TEXT,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID REFERENCES business(id) ON DELETE SET NULL,
    provider VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    external_event_id VARCHAR(180),
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED','IGNORED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_webhook_provider_event
    ON webhook_event(provider, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(30) NOT NULL CHECK (channel IN ('SMS','WHATSAPP','EMAIL','PUSH')),
    recipient VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id),
    call_id UUID REFERENCES call_session(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','PREPARING','READY','DELIVERING','COMPLETED','CANCELLED')),
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    total NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    delivery_type VARCHAR(30),
    delivery_address TEXT,
    idempotency_key VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_order_idempotency
    ON customer_order(business_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    total_price NUMERIC(12,2) NOT NULL CHECK (total_price >= 0)
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID REFERENCES business(id) ON DELETE SET NULL,
    user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ai_agent_id UUID REFERENCES ai_agent(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    result VARCHAR(30) NOT NULL CHECK (result IN ('SUCCESS','DENIED','FAILED')),
    correlation_id VARCHAR(120),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_business_created ON audit_log(business_id, created_at DESC);

INSERT INTO role(code, name) VALUES
('PLATFORM_ADMIN', 'Administrador de plataforma'),
('BUSINESS_ADMIN', 'Administrador de negocio'),
('OPERATOR', 'Operador')
ON CONFLICT (code) DO NOTHING;
