-- V89 Delivery tenant integrity hardening
--
-- V36 already protects business_delivery.operation_id with a tenant-aware
-- composite foreign key. Complete the relational boundary for the remaining
-- tenant-owned references: customer_id, order_id, and delivery_zone_id.
--
-- Keep the original deletion semantics:
-- - deleting a customer clears only customer_id, never business_id;
-- - deleting a referenced order clears only order_id, never business_id;
-- - deleting a referenced delivery zone remains restricted.

ALTER TABLE public.business_order
    ADD CONSTRAINT uq_business_order_id_business
        UNIQUE (id, business_id);

ALTER TABLE public.business_delivery
    ADD CONSTRAINT fk_business_delivery_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id),
    ADD CONSTRAINT fk_business_delivery_order_tenant
        FOREIGN KEY (order_id, business_id)
        REFERENCES public.business_order(id, business_id)
        ON DELETE SET NULL (order_id),
    ADD CONSTRAINT fk_business_delivery_zone_tenant
        FOREIGN KEY (delivery_zone_id, business_id)
        REFERENCES public.delivery_zone(id, business_id)
        ON DELETE RESTRICT;
