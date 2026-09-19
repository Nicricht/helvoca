-- V54: provider-specific WhatsApp identity for tenant routing.
--
-- Telephony provider/external_id remain dedicated to Voice.
-- WhatsApp gets its own provider + external id so Meta phone_number_id can
-- resolve the owning business without overloading Twilio identifiers.

ALTER TABLE phone_number
    ADD COLUMN whatsapp_provider VARCHAR(30) NOT NULL DEFAULT 'TWILIO_WHATSAPP',
    ADD COLUMN whatsapp_external_id VARCHAR(100);

CREATE UNIQUE INDEX uq_phone_number_whatsapp_provider_external
    ON phone_number(whatsapp_provider, whatsapp_external_id)
    WHERE whatsapp_external_id IS NOT NULL;
