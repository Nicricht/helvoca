-- V43 Voice -> WhatsApp continuity
-- A phone number must be explicitly enabled as a WhatsApp sender for its tenant.
-- Existing numbers remain disabled so deployment cannot activate real messaging.
ALTER TABLE phone_number
    ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_phone_number_business_whatsapp_sender
    ON phone_number(business_id, active, whatsapp_enabled);
