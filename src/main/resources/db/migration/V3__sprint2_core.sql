CREATE TABLE customer (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(180),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_business ON customer(business_id);
CREATE INDEX idx_customer_business_phone ON customer(business_id, phone);

CREATE TABLE service (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    price NUMERIC(12,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_service_business_name UNIQUE (business_id, name)
);

CREATE INDEX idx_service_business ON service(business_id);

CREATE TABLE booking (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customer(id),
    service_id UUID NOT NULL REFERENCES service(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_booking_period CHECK (end_at > start_at)
);

CREATE INDEX idx_booking_business_start ON booking(business_id, start_at);
CREATE INDEX idx_booking_service_period ON booking(business_id, service_id, start_at, end_at);
CREATE INDEX idx_booking_customer ON booking(business_id, customer_id);

CREATE TABLE knowledge_item (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    content TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_business_active ON knowledge_item(business_id, active);
