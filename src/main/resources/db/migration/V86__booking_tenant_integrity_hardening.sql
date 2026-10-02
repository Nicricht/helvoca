-- V86 Booking tenant integrity hardening
--
-- RLS protects ordinary tenant runtime access, but privileged SYSTEM/owner
-- writes must also be unable to persist cross-tenant relational references.
-- Booking already carries business_id, so enforce customer/service ownership
-- at the PostgreSQL foreign-key boundary.

ALTER TABLE public.service
    ADD CONSTRAINT uq_service_id_business
        UNIQUE (id, business_id);

ALTER TABLE public.booking
    ADD CONSTRAINT fk_booking_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_booking_service_tenant
        FOREIGN KEY (service_id, business_id)
        REFERENCES public.service(id, business_id)
        ON DELETE RESTRICT;
