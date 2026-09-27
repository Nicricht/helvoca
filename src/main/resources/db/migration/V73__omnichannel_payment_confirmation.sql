-- V73: Omnichannel commerce payment success confirmation.
-- V72 is reserved by Inventory V1. This branch intentionally starts from main
-- and does not copy Inventory V1 into the omnichannel branch.
--
-- The new purpose is backend-rendered only after provider-verified SUCCEEDED.
-- Delivery remains governed by the existing outbound delivery safety switch.

ALTER TABLE public.outbound_message
    DROP CONSTRAINT IF EXISTS ck_outbound_message_purpose;

ALTER TABLE public.outbound_message
    ADD CONSTRAINT ck_outbound_message_purpose CHECK (purpose IN (
        'PAYMENT_LINK','PAYMENT_CONFIRMATION','BOOKING_CONFIRMATION','MEETING_LINK','ORDER_STATUS',
        'QUOTE','REMINDER','DELIVERY_STATUS','INCIDENT_NOTICE','PRODUCT_SHOWCASE'
    ));
