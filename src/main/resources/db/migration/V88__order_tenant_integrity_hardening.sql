-- V88 Order tenant integrity hardening
--
-- V36 already protects business_order.operation_id with a tenant-aware
-- composite foreign key. Complete the relational boundary for the remaining
-- tenant-owned references: customer_id and delivery_zone_id.
--
-- Keep the original deletion semantics:
-- - deleting a customer clears only customer_id, never business_id;
-- - deleting a referenced delivery zone remains restricted.

ALTER TABLE public.delivery_zone
    ADD CONSTRAINT uq_delivery_zone_id_business
        UNIQUE (id, business_id);

ALTER TABLE public.business_order
    ADD CONSTRAINT fk_business_order_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id),
    ADD CONSTRAINT fk_business_order_delivery_zone_tenant
        FOREIGN KEY (delivery_zone_id, business_id)
        REFERENCES public.delivery_zone(id, business_id)
        ON DELETE RESTRICT;
