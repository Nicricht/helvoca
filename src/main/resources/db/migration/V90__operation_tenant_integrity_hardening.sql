-- V90 Universal operation tenant integrity hardening
--
-- business_operation is the tenant-owned envelope for all operation types.
-- Its customer and delivery-zone references must therefore agree with the
-- operation business_id even for privileged owner/SYSTEM-equivalent writes.
--
-- Preserve the original delete semantics:
-- - deleting a customer clears only customer_id, never business_id;
-- - deleting a referenced delivery zone remains restricted.

ALTER TABLE public.business_operation
    ADD CONSTRAINT fk_business_operation_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id),
    ADD CONSTRAINT fk_business_operation_delivery_zone_tenant
        FOREIGN KEY (delivery_zone_id, business_id)
        REFERENCES public.delivery_zone(id, business_id)
        ON DELETE RESTRICT;
