CREATE TABLE business_automation_policy (
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    operation_type VARCHAR(20) NOT NULL,
    auto_execute BOOLEAN NOT NULL DEFAULT TRUE,
    customer_confirmation VARCHAR(20) NOT NULL,
    payment_requirement VARCHAR(30) NOT NULL DEFAULT 'NONE',
    retry_policy VARCHAR(30) NOT NULL DEFAULT 'SAFE_AUTOMATIC',
    max_auto_retries SMALLINT NOT NULL DEFAULT 2,
    escalation_policy VARCHAR(40) NOT NULL DEFAULT 'ONLY_IF_UNRESOLVABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (business_id, operation_type),
    CONSTRAINT ck_business_automation_policy_type
        CHECK (operation_type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING','PAYMENT')),
    CONSTRAINT ck_business_automation_policy_confirmation
        CHECK (customer_confirmation IN ('NONE','EXPLICIT')),
    CONSTRAINT ck_business_automation_policy_payment
        CHECK (payment_requirement IN ('NONE','REQUIRED_AFTER_CONFIRMATION')),
    CONSTRAINT ck_business_automation_policy_retry
        CHECK (retry_policy IN ('NONE','SAFE_AUTOMATIC')),
    CONSTRAINT ck_business_automation_policy_retry_count
        CHECK (max_auto_retries BETWEEN 0 AND 5),
    CONSTRAINT ck_business_automation_policy_escalation
        CHECK (escalation_policy IN ('NEVER','ONLY_IF_UNRESOLVABLE')),
    CONSTRAINT ck_business_automation_policy_retry_consistency
        CHECK ((retry_policy = 'NONE' AND max_auto_retries = 0)
            OR (retry_policy = 'SAFE_AUTOMATIC' AND max_auto_retries BETWEEN 1 AND 5)),
    CONSTRAINT ck_business_automation_policy_payment_self_reference
        CHECK (operation_type <> 'PAYMENT' OR payment_requirement = 'NONE'),
    CONSTRAINT ck_business_automation_policy_transaction_confirmation_floor
        CHECK (operation_type NOT IN ('ORDER','DELIVERY','BOOKING','PAYMENT')
            OR customer_confirmation = 'EXPLICIT')
);

CREATE INDEX idx_business_automation_policy_business
    ON business_automation_policy(business_id);

CREATE OR REPLACE FUNCTION touch_business_automation_policy_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_business_automation_policy_updated_at
BEFORE UPDATE ON business_automation_policy
FOR EACH ROW
EXECUTE FUNCTION touch_business_automation_policy_updated_at();
