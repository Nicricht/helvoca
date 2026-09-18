-- V46 WhatsApp sender certification and tenant-safe incident outbound linkage

ALTER TABLE phone_number
    ADD COLUMN whatsapp_certified_at TIMESTAMPTZ;

ALTER TABLE outbound_message
    ADD CONSTRAINT uq_outbound_message_id_business UNIQUE (id, business_id);

ALTER TABLE booking_incident_recipient
    DROP CONSTRAINT IF EXISTS booking_incident_recipient_outbound_message_id_fkey;

ALTER TABLE booking_incident_recipient
    ADD CONSTRAINT fk_booking_incident_recipient_outbound_tenant
        FOREIGN KEY (outbound_message_id, business_id)
        REFERENCES outbound_message(id, business_id)
        ON DELETE RESTRICT;
