-- V91 Typed projection tenant integrity hardening
--
-- QUOTE, LEAD and REQUEST are tenant-owned projections of business_operation.
-- Enforce that every tenant-owned reference agrees with the projection business_id
-- even for privileged owner/SYSTEM-equivalent writes.
--
-- Preserve existing delete semantics:
-- - quote/lead customer deletion clears only customer_id;
-- - request customer/call references keep the original NO ACTION semantics;
-- - projection operation deletion remains RESTRICT.

ALTER TABLE public.call_session
    ADD CONSTRAINT uq_call_session_id_business
        UNIQUE (id, business_id);

ALTER TABLE public.business_quote
    ADD CONSTRAINT fk_business_quote_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_business_quote_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id);

ALTER TABLE public.business_lead
    ADD CONSTRAINT fk_business_lead_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_business_lead_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id);

ALTER TABLE public.business_request
    ADD CONSTRAINT fk_business_request_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_business_request_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id),
    ADD CONSTRAINT fk_business_request_call_tenant
        FOREIGN KEY (call_id, business_id)
        REFERENCES public.call_session(id, business_id);
