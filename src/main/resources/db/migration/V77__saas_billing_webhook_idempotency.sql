-- V77 SaaS billing webhook idempotency
-- Remember the last provider invoice applied to a business subscription so
-- Mercado Pago webhook retries cannot re-apply the same billing transition.

ALTER TABLE business_subscription
    ADD COLUMN last_billing_invoice_id VARCHAR(160);

CREATE INDEX idx_business_subscription_last_billing_invoice
    ON business_subscription(last_billing_invoice_id)
    WHERE last_billing_invoice_id IS NOT NULL;

COMMENT ON COLUMN business_subscription.last_billing_invoice_id IS
    'Last Mercado Pago SaaS invoice applied to subscription state. Used for effect-level webhook idempotency.';
