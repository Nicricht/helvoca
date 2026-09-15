-- Universal operation envelope and structured ORDER workflow state.
-- Typed business_order remains the immutable confirmed order projection.

CREATE TABLE business_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    confirmation_token UUID,
    contact_name VARCHAR(180),
    contact_phone VARCHAR(30),
    fulfillment_type VARCHAR(20),
    delivery_zone_id UUID REFERENCES delivery_zone(id) ON DELETE RESTRICT,
    delivery_address TEXT,
    subtotal NUMERIC(12,2),
    delivery_fee NUMERIC(12,2),
    total NUMERIC(12,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    metadata_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_operation_type CHECK (type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST')),
    CONSTRAINT ck_business_operation_status CHECK (status IN ('DRAFT','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED','FAILED')),
    CONSTRAINT ck_business_operation_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_business_operation_revision CHECK (revision > 0),
    CONSTRAINT ck_business_operation_money CHECK (
        (subtotal IS NULL OR subtotal >= 0)
        AND (delivery_fee IS NULL OR delivery_fee >= 0)
        AND (total IS NULL OR total >= 0)
    ),
    CONSTRAINT ck_business_operation_total CHECK (
        total IS NULL OR subtotal IS NULL OR delivery_fee IS NULL OR total = subtotal + delivery_fee
    ),
    CONSTRAINT ck_business_operation_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_business_operation_fulfillment CHECK (fulfillment_type IS NULL OR fulfillment_type IN ('PICKUP','DELIVERY')),
    CONSTRAINT ck_business_operation_confirmation CHECK (
        (status = 'AWAITING_CONFIRMATION' AND confirmation_token IS NOT NULL)
        OR (status <> 'AWAITING_CONFIRMATION')
    )
);

CREATE INDEX idx_business_operation_business_created
    ON business_operation(business_id, created_at DESC);
CREATE INDEX idx_business_operation_source_reference
    ON business_operation(business_id, source_reference_id, updated_at DESC);
CREATE INDEX idx_business_operation_customer
    ON business_operation(business_id, customer_id, updated_at DESC);
CREATE INDEX idx_business_operation_phone
    ON business_operation(business_id, contact_phone, updated_at DESC);

CREATE TABLE business_operation_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES business_operation(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE RESTRICT,
    item_name VARCHAR(180) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    modifiers_json JSONB,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_operation_item_quantity CHECK (quantity > 0 AND quantity <= 100),
    CONSTRAINT ck_business_operation_item_money CHECK (unit_price >= 0 AND line_total >= 0),
    CONSTRAINT ck_business_operation_item_total CHECK (line_total = unit_price * quantity),
    CONSTRAINT ck_business_operation_item_modifiers CHECK (
        modifiers_json IS NULL OR jsonb_typeof(modifiers_json) IN ('object','array')
    )
);

CREATE INDEX idx_business_operation_item_operation
    ON business_operation_item(operation_id, created_at);

ALTER TABLE business_order
    ADD COLUMN operation_id UUID;

ALTER TABLE business_order_line
    ADD COLUMN modifiers_json JSONB;

-- Backfill every existing order into the universal operation envelope. Reusing
-- the historical order UUID makes the migration deterministic and keeps a
-- simple one-to-one mapping for pre-V23 records.
INSERT INTO business_operation (
    id, business_id, customer_id, source_reference_id, type, status, source,
    revision, confirmation_token, contact_name, contact_phone,
    fulfillment_type, delivery_zone_id, delivery_address,
    subtotal, delivery_fee, total, currency, metadata_json,
    created_at, updated_at
)
SELECT
    o.id,
    o.business_id,
    o.customer_id,
    o.source_reference_id,
    'ORDER',
    CASE WHEN o.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
    o.source,
    1,
    NULL,
    o.contact_name,
    o.contact_phone,
    o.fulfillment_type,
    o.delivery_zone_id,
    o.delivery_address,
    o.subtotal,
    o.delivery_fee,
    o.total,
    o.currency,
    jsonb_build_object('migratedFromOrderStatus', o.status),
    o.created_at,
    o.updated_at
FROM business_order o
ON CONFLICT (id) DO NOTHING;

INSERT INTO business_operation_item (
    id, operation_id, catalog_item_id, item_name, quantity,
    unit_price, line_total, modifiers_json, notes, created_at
)
SELECT
    l.id,
    l.order_id,
    l.catalog_item_id,
    l.item_name,
    l.quantity,
    l.unit_price,
    l.line_total,
    NULL,
    l.notes,
    l.created_at
FROM business_order_line l
ON CONFLICT (id) DO NOTHING;

UPDATE business_order
SET operation_id = id
WHERE operation_id IS NULL;

ALTER TABLE business_order
    ALTER COLUMN operation_id SET NOT NULL,
    ADD CONSTRAINT fk_business_order_operation
        FOREIGN KEY (operation_id) REFERENCES business_operation(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_business_order_operation UNIQUE (operation_id);

CREATE INDEX idx_business_order_operation
    ON business_order(operation_id);
