-- DELIVERY becomes an autonomous universal operation while ORDER keeps its
-- existing fulfillment fields. business_delivery is only the typed projection
-- of a standalone DELIVERY operation and does not duplicate historical ORDERs.

CREATE TABLE business_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id UUID NOT NULL REFERENCES business_operation(id) ON DELETE RESTRICT,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customer(id) ON DELETE SET NULL,
    source_reference_id UUID,
    order_id UUID REFERENCES business_order(id) ON DELETE SET NULL,
    contact_name VARCHAR(180),
    contact_phone VARCHAR(30),
    delivery_zone_id UUID NOT NULL REFERENCES delivery_zone(id) ON DELETE RESTRICT,
    delivery_address TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    source VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_delivery_operation UNIQUE (operation_id),
    CONSTRAINT ck_business_delivery_status CHECK (status IN ('CONFIRMED','IN_TRANSIT','DELIVERED','CANCELLED')),
    CONSTRAINT ck_business_delivery_source CHECK (source IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_business_delivery_fee CHECK (fee >= 0),
    CONSTRAINT ck_business_delivery_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_business_delivery_business_created
    ON business_delivery(business_id, created_at DESC);
CREATE INDEX idx_business_delivery_customer_created
    ON business_delivery(business_id, customer_id, created_at DESC);
CREATE INDEX idx_business_delivery_phone_created
    ON business_delivery(business_id, contact_phone, created_at DESC);
CREATE INDEX idx_business_delivery_source_reference
    ON business_delivery(business_id, source_reference_id, created_at DESC);
CREATE INDEX idx_business_delivery_order
    ON business_delivery(business_id, order_id, created_at DESC);

-- DELIVERY gains its own transactional tools. Only tenants that had already
-- opted into both existing delivery tools receive the new tools. Tenants that
-- never enabled delivery remain untouched.
INSERT INTO ai_agent_capability (ai_agent_id, capability)
SELECT aa.id, added.capability
FROM ai_agent aa
CROSS JOIN (VALUES
    ('QUOTE_DELIVERY'),
    ('UPDATE_DELIVERY'),
    ('CREATE_DELIVERY'),
    ('GET_DELIVERY_STATUS'),
    ('CANCEL_DELIVERY')
) AS added(capability)
WHERE EXISTS (
    SELECT 1 FROM ai_agent_capability c
    WHERE c.ai_agent_id = aa.id AND c.capability = 'LIST_DELIVERY_ZONES'
)
AND EXISTS (
    SELECT 1 FROM ai_agent_capability c
    WHERE c.ai_agent_id = aa.id AND c.capability = 'VALIDATE_DELIVERY_ADDRESS'
)
ON CONFLICT (ai_agent_id, capability) DO NOTHING;
