-- V36 Exactly-Once / Concurrency Hardening
-- Strengthen typed operation projections so operation ownership cannot cross tenants.
-- Existing UNIQUE(operation_id) constraints remain the final duplicate-materialization guard.

ALTER TABLE business_order
    ADD CONSTRAINT fk_business_order_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE business_delivery
    ADD CONSTRAINT fk_business_delivery_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE business_payment
    ADD CONSTRAINT fk_business_payment_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE booking
    ADD CONSTRAINT fk_booking_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id)
        ON DELETE RESTRICT;
