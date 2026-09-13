CREATE TABLE business_subscription (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE REFERENCES business(id) ON DELETE CASCADE,
    plan_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    grace_until TIMESTAMPTZ,
    external_customer_id VARCHAR(160),
    external_subscription_id VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_business_subscription_plan CHECK (plan_code IN ('BASIC','PRO','BUSINESS')),
    CONSTRAINT ck_business_subscription_status CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','SUSPENDED','CANCELED')),
    CONSTRAINT ck_business_subscription_period CHECK (current_period_end > current_period_start)
);

CREATE INDEX idx_business_subscription_status ON business_subscription(status);
CREATE UNIQUE INDEX uq_business_subscription_external
    ON business_subscription(external_subscription_id)
    WHERE external_subscription_id IS NOT NULL;

-- Preserve today's production behavior for existing tenants: current global capacity is 10,
-- which maps to PRO. New self-service registrations are created as BASIC trials by the app.
INSERT INTO business_subscription(
    id, business_id, plan_code, status,
    current_period_start, current_period_end, created_at, updated_at)
SELECT gen_random_uuid(), b.id, 'PRO', 'ACTIVE',
       date_trunc('month', now()), date_trunc('month', now()) + interval '1 month', now(), now()
FROM business b
ON CONFLICT (business_id) DO NOTHING;
