-- V42 Plans, Entitlements & Billing
-- Commercial plan configuration becomes data. V41 remains the usage source of truth.

CREATE TABLE commercial_plan (
    code VARCHAR(40) PRIMARY KEY,
    public_code VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    monthly_price_clp INTEGER,
    currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    custom_pricing BOOLEAN NOT NULL DEFAULT FALSE,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_commercial_plan_code_non_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_commercial_plan_public_code_non_blank CHECK (btrim(public_code) <> ''),
    CONSTRAINT ck_commercial_plan_display_name_non_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_commercial_plan_currency_non_blank CHECK (btrim(currency) <> ''),
    CONSTRAINT ck_commercial_plan_price_non_negative CHECK (monthly_price_clp IS NULL OR monthly_price_clp >= 0),
    CONSTRAINT ck_commercial_plan_sort_non_negative CHECK (sort_order >= 0)
);

CREATE INDEX idx_commercial_plan_active_sort
    ON commercial_plan(active, sort_order, code);

CREATE TABLE commercial_plan_entitlement (
    plan_code VARCHAR(40) NOT NULL REFERENCES commercial_plan(code) ON DELETE CASCADE,
    entitlement_key VARCHAR(80) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    meter_key VARCHAR(80),
    limit_value NUMERIC(20,6) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    hard_limit BOOLEAN NOT NULL DEFAULT FALSE,
    overage_unit_size NUMERIC(20,6),
    overage_price_clp INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plan_code, entitlement_key),
    CONSTRAINT ck_plan_entitlement_key_non_blank CHECK (btrim(entitlement_key) <> ''),
    CONSTRAINT ck_plan_entitlement_kind CHECK (kind IN ('USAGE','CAPACITY')),
    CONSTRAINT ck_plan_entitlement_meter_shape CHECK (
        (kind = 'USAGE' AND meter_key IS NOT NULL AND btrim(meter_key) <> '')
        OR (kind = 'CAPACITY' AND meter_key IS NULL)
    ),
    CONSTRAINT ck_plan_entitlement_limit_non_negative CHECK (limit_value >= 0),
    CONSTRAINT ck_plan_entitlement_unit_non_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_plan_entitlement_overage_unit CHECK (overage_unit_size IS NULL OR overage_unit_size > 0),
    CONSTRAINT ck_plan_entitlement_overage_price CHECK (overage_price_clp IS NULL OR overage_price_clp >= 0),
    CONSTRAINT ck_plan_entitlement_capacity_no_overage CHECK (
        kind = 'USAGE' OR (overage_unit_size IS NULL AND overage_price_clp IS NULL)
    )
);

CREATE INDEX idx_plan_entitlement_meter
    ON commercial_plan_entitlement(meter_key)
    WHERE meter_key IS NOT NULL;

INSERT INTO commercial_plan(
    code, public_code, display_name, monthly_price_clp, currency,
    custom_pricing, recommended, active, sort_order
) VALUES
    ('BASIC', 'EMPRENDE', 'Emprende', 24990, 'CLP', FALSE, FALSE, TRUE, 1),
    ('PRO', 'NEGOCIO', 'Negocio', 39990, 'CLP', FALSE, TRUE, TRUE, 2),
    ('BUSINESS', 'PRO', 'Pro', 69990, 'CLP', FALSE, FALSE, TRUE, 3),
    ('ENTERPRISE', 'ENTERPRISE', 'Enterprise', 119990, 'CLP', TRUE, FALSE, TRUE, 4);

INSERT INTO commercial_plan_entitlement(
    plan_code, entitlement_key, kind, meter_key, limit_value, unit,
    hard_limit, overage_unit_size, overage_price_clp
) VALUES
    ('BASIC', 'VOICE_SECONDS', 'USAGE', 'VOICE_SECONDS', 6000, 'SECONDS', FALSE, 60, 149),
    ('BASIC', 'CONCURRENT_CALLS', 'CAPACITY', NULL, 1, 'COUNT', TRUE, NULL, NULL),
    ('PRO', 'VOICE_SECONDS', 'USAGE', 'VOICE_SECONDS', 15000, 'SECONDS', FALSE, 60, 129),
    ('PRO', 'CONCURRENT_CALLS', 'CAPACITY', NULL, 3, 'COUNT', TRUE, NULL, NULL),
    ('BUSINESS', 'VOICE_SECONDS', 'USAGE', 'VOICE_SECONDS', 30000, 'SECONDS', FALSE, 60, 109),
    ('BUSINESS', 'CONCURRENT_CALLS', 'CAPACITY', NULL, 10, 'COUNT', TRUE, NULL, NULL),
    ('ENTERPRISE', 'VOICE_SECONDS', 'USAGE', 'VOICE_SECONDS', 60000, 'SECONDS', FALSE, NULL, NULL),
    ('ENTERPRISE', 'CONCURRENT_CALLS', 'CAPACITY', NULL, 10, 'COUNT', TRUE, NULL, NULL);

-- Replace finite enum-like constraints with catalog referential integrity.
ALTER TABLE business_subscription DROP CONSTRAINT ck_business_subscription_plan;
ALTER TABLE business_subscription DROP CONSTRAINT ck_business_subscription_pending_plan;
ALTER TABLE business_subscription ALTER COLUMN plan_code TYPE VARCHAR(40);
ALTER TABLE business_subscription ALTER COLUMN pending_plan_code TYPE VARCHAR(40);
ALTER TABLE business_subscription
    ADD CONSTRAINT fk_business_subscription_plan
    FOREIGN KEY (plan_code) REFERENCES commercial_plan(code);
ALTER TABLE business_subscription
    ADD CONSTRAINT fk_business_subscription_pending_plan
    FOREIGN KEY (pending_plan_code) REFERENCES commercial_plan(code);

-- Global commercial reference data is readable but immutable to application runtime roles.
GRANT SELECT ON TABLE commercial_plan, commercial_plan_entitlement TO helvoca_runtime, helvoca_system;
REVOKE INSERT, UPDATE, DELETE ON TABLE commercial_plan, commercial_plan_entitlement FROM helvoca_runtime, helvoca_system;

COMMENT ON TABLE commercial_plan IS
    'V42 provider-neutral commercial plan catalog. Prices and public presentation are data, not Java enum constants.';
COMMENT ON TABLE commercial_plan_entitlement IS
    'V42 generic plan entitlements. USAGE rules consume V41 usage_meter_event; CAPACITY rules gate instantaneous resources.';
