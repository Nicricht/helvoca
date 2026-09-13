-- Existing tenants created by V14 used technical plan PRO (10 concurrent calls).
-- Move them to technical BUSINESS, which is the new public "Pro" tier and keeps 10 concurrent calls.
UPDATE business_subscription
SET plan_code = 'BUSINESS'
WHERE plan_code = 'PRO';

ALTER TABLE business_subscription DROP CONSTRAINT ck_business_subscription_plan;
ALTER TABLE business_subscription
    ADD CONSTRAINT ck_business_subscription_plan
    CHECK (plan_code IN ('BASIC','PRO','BUSINESS','ENTERPRISE'));
