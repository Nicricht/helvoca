-- V87 Payment tenant integrity hardening
--
-- V36 already protects the PAYMENT projection operation_id with a tenant-aware
-- composite foreign key. Complete the relational boundary for the remaining
-- tenant-owned references: customer_id and target_operation_id.

ALTER TABLE public.business_payment
    ADD CONSTRAINT fk_business_payment_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id),
    ADD CONSTRAINT fk_business_payment_target_operation_tenant
        FOREIGN KEY (target_operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE RESTRICT;
