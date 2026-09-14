CREATE TABLE catalog_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    service_id UUID REFERENCES service(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    unit_price NUMERIC(12,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_catalog_item_kind CHECK (kind IN ('SERVICE','PRODUCT')),
    CONSTRAINT ck_catalog_item_price CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT uq_catalog_item_business_name UNIQUE (business_id, name),
    CONSTRAINT uq_catalog_item_service UNIQUE (service_id)
);

CREATE INDEX idx_catalog_item_business_active
    ON catalog_item(business_id, active, name);

INSERT INTO catalog_item (business_id, kind, service_id, name, description, unit_price, active, created_at, updated_at)
SELECT business_id, 'SERVICE', id, name, description, price, active, created_at, updated_at
FROM service
ON CONFLICT DO NOTHING;

CREATE TABLE commerce_profile (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    pickup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    flat_delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    delivery_instructions TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_commerce_profile_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_commerce_profile_delivery_fee CHECK (flat_delivery_fee >= 0)
);

CREATE TABLE commerce_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    call_id UUID REFERENCES call_session(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    fulfillment_type VARCHAR(20),
    delivery_address TEXT,
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    subtotal NUMERIC(12,2),
    delivery_fee NUMERIC(12,2),
    total NUMERIC(12,2),
    title VARCHAR(200),
    notes TEXT,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_commerce_operation_type CHECK (type IN ('ORDER','QUOTE','LEAD')),
    CONSTRAINT ck_commerce_operation_status CHECK (status IN ('DRAFT','CONFIRMED','OPEN','CLOSED','CANCELLED')),
    CONSTRAINT ck_commerce_operation_fulfillment CHECK (fulfillment_type IS NULL OR fulfillment_type IN ('PICKUP','DELIVERY')),
    CONSTRAINT ck_commerce_operation_amounts CHECK (
        (subtotal IS NULL OR subtotal >= 0) AND
        (delivery_fee IS NULL OR delivery_fee >= 0) AND
        (total IS NULL OR total >= 0)
    )
);

CREATE INDEX idx_commerce_operation_business_created
    ON commerce_operation(business_id, created_at DESC);
CREATE INDEX idx_commerce_operation_customer_created
    ON commerce_operation(business_id, customer_id, created_at DESC);
CREATE INDEX idx_commerce_operation_status
    ON commerce_operation(business_id, type, status);

CREATE TABLE commerce_operation_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES commerce_operation(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id),
    item_name VARCHAR(150) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    notes TEXT,
    CONSTRAINT ck_commerce_operation_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_commerce_operation_item_amounts CHECK (unit_price >= 0 AND line_total >= 0)
);

CREATE INDEX idx_commerce_operation_item_operation
    ON commerce_operation_item(operation_id);
