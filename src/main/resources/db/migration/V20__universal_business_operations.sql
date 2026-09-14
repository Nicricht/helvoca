CREATE TABLE business_operation_capability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    capability VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_operation_capability UNIQUE (business_id, capability),
    CONSTRAINT ck_business_operation_capability CHECK (capability IN ('CATALOG','ORDER','DELIVERY','QUOTE','LEAD'))
);

CREATE INDEX idx_business_operation_capability_business
    ON business_operation_capability(business_id);

CREATE TABLE catalog_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(12,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    duration_minutes INTEGER,
    legacy_service_id UUID UNIQUE,
    metadata_json TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_catalog_item_business_kind_name UNIQUE (business_id, kind, name),
    CONSTRAINT ck_catalog_item_kind CHECK (kind IN ('SERVICE','PRODUCT')),
    CONSTRAINT ck_catalog_item_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_catalog_item_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT ck_catalog_item_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_catalog_item_business_active
    ON catalog_item(business_id, active, name);

INSERT INTO catalog_item (
    business_id, kind, name, description, price, currency,
    duration_minutes, legacy_service_id, active, created_at, updated_at
)
SELECT
    business_id, 'SERVICE', name, description, price, 'CLP',
    duration_minutes, id, active, created_at, updated_at
FROM service
ON CONFLICT (legacy_service_id) DO NOTHING;

CREATE OR REPLACE FUNCTION sync_service_to_catalog_item()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE catalog_item
           SET active = FALSE,
               legacy_service_id = NULL,
               updated_at = NOW()
         WHERE legacy_service_id = OLD.id;
        RETURN OLD;
    END IF;

    INSERT INTO catalog_item (
        business_id, kind, name, description, price, currency,
        duration_minutes, legacy_service_id, active, created_at, updated_at
    ) VALUES (
        NEW.business_id, 'SERVICE', NEW.name, NEW.description, NEW.price, 'CLP',
        NEW.duration_minutes, NEW.id, NEW.active, NEW.created_at, NEW.updated_at
    )
    ON CONFLICT (legacy_service_id) DO UPDATE SET
        business_id = EXCLUDED.business_id,
        kind = 'SERVICE',
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        price = EXCLUDED.price,
        currency = EXCLUDED.currency,
        duration_minutes = EXCLUDED.duration_minutes,
        active = EXCLUDED.active,
        updated_at = EXCLUDED.updated_at;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_service_to_catalog_item
AFTER INSERT OR UPDATE OR DELETE ON service
FOR EACH ROW EXECUTE FUNCTION sync_service_to_catalog_item();

CREATE TABLE delivery_zone (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    minimum_order NUMERIC(12,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delivery_zone_business_name UNIQUE (business_id, name),
    CONSTRAINT ck_delivery_zone_fee CHECK (fee >= 0),
    CONSTRAINT ck_delivery_zone_minimum CHECK (minimum_order IS NULL OR minimum_order >= 0)
);

CREATE INDEX idx_delivery_zone_business_active
    ON delivery_zone(business_id, active, name);

CREATE TABLE business_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    contact_name VARCHAR(180),
    contact_phone VARCHAR(30),
    fulfillment_type VARCHAR(20) NOT NULL,
    delivery_zone_id UUID REFERENCES delivery_zone(id) ON DELETE RESTRICT,
    delivery_address TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    subtotal NUMERIC(12,2) NOT NULL,
    delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    source VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_order_fulfillment CHECK (fulfillment_type IN ('PICKUP','DELIVERY')),
    CONSTRAINT ck_business_order_status CHECK (status IN ('CONFIRMED','PREPARING','READY','DISPATCHED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_business_order_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_business_order_money CHECK (subtotal >= 0 AND delivery_fee >= 0 AND total >= 0),
    CONSTRAINT ck_business_order_total CHECK (total = subtotal + delivery_fee),
    CONSTRAINT ck_business_order_delivery CHECK (
        (fulfillment_type = 'PICKUP' AND delivery_zone_id IS NULL AND delivery_address IS NULL)
        OR
        (fulfillment_type = 'DELIVERY' AND delivery_zone_id IS NOT NULL AND delivery_address IS NOT NULL)
    )
);

CREATE INDEX idx_business_order_business_created
    ON business_order(business_id, created_at DESC);
CREATE INDEX idx_business_order_customer_created
    ON business_order(business_id, customer_id, created_at DESC);
CREATE INDEX idx_business_order_phone_created
    ON business_order(business_id, contact_phone, created_at DESC);

CREATE TABLE business_order_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES business_order(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id),
    item_name VARCHAR(180) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_order_line_quantity CHECK (quantity > 0 AND quantity <= 100),
    CONSTRAINT ck_business_order_line_money CHECK (unit_price >= 0 AND line_total >= 0),
    CONSTRAINT ck_business_order_line_total CHECK (line_total = unit_price * quantity)
);

CREATE INDEX idx_business_order_line_order
    ON business_order_line(order_id, created_at);

CREATE TABLE business_quote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    contact_name VARCHAR(180),
    contact_phone VARCHAR(30),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    amount NUMERIC(12,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_quote_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT ck_business_quote_status CHECK (status IN ('REQUESTED','READY','ACCEPTED','REJECTED','CANCELLED')),
    CONSTRAINT ck_business_quote_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API'))
);

CREATE INDEX idx_business_quote_business_created
    ON business_quote(business_id, created_at DESC);

CREATE TABLE business_lead (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    name VARCHAR(180) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(180),
    interest TEXT NOT NULL,
    budget NUMERIC(12,2),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_lead_budget CHECK (budget IS NULL OR budget >= 0),
    CONSTRAINT ck_business_lead_status CHECK (status IN ('NEW','CONTACTED','QUALIFIED','WON','LOST')),
    CONSTRAINT ck_business_lead_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API'))
);

CREATE INDEX idx_business_lead_business_created
    ON business_lead(business_id, created_at DESC);
