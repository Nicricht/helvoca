ALTER TABLE business_subscription
    ADD COLUMN max_concurrent_calls_override INTEGER,
    ADD COLUMN included_minutes_override INTEGER,
    ADD COLUMN monthly_price_clp_override INTEGER,
    ADD COLUMN overage_per_minute_clp_override INTEGER;

-- Preserve legacy entitlements while moving to the public commercial catalog.
UPDATE business_subscription
SET max_concurrent_calls_override = 2,
    included_minutes_override = 300,
    plan_code = 'EMPRENDE'
WHERE plan_code = 'BASIC';

UPDATE business_subscription
SET max_concurrent_calls_override = 10,
    included_minutes_override = 2000
WHERE plan_code = 'PRO';

UPDATE business_subscription
SET max_concurrent_calls_override = 50,
    included_minutes_override = 10000,
    plan_code = 'ENTERPRISE'
WHERE plan_code = 'BUSINESS';

ALTER TABLE business_subscription DROP CONSTRAINT ck_business_subscription_plan;
ALTER TABLE business_subscription
    ADD CONSTRAINT ck_business_subscription_plan
    CHECK (plan_code IN ('EMPRENDE','NEGOCIO','PRO','ENTERPRISE'));

ALTER TABLE business_subscription
    ADD CONSTRAINT ck_business_subscription_overrides
    CHECK (
        (max_concurrent_calls_override IS NULL OR max_concurrent_calls_override > 0)
        AND (included_minutes_override IS NULL OR included_minutes_override >= 0)
        AND (monthly_price_clp_override IS NULL OR monthly_price_clp_override >= 0)
        AND (overage_per_minute_clp_override IS NULL OR overage_per_minute_clp_override >= 0)
    );
