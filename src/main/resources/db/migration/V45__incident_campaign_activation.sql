-- V45 Incident campaign activation
-- Adds the state needed to activate a prepared campaign through the existing
-- durable outbound-message outbox. No configuration flag is enabled here.

ALTER TABLE booking_incident_campaign
    DROP CONSTRAINT IF EXISTS ck_booking_incident_campaign_status;
ALTER TABLE booking_incident_campaign
    ADD CONSTRAINT ck_booking_incident_campaign_status
        CHECK (status IN ('PREPARED', 'ACTIVATED', 'CANCELLED', 'COMPLETED'));

ALTER TABLE booking_incident_recipient
    ADD COLUMN outbound_message_id UUID REFERENCES outbound_message(id) ON DELETE SET NULL;

ALTER TABLE booking_incident_recipient
    DROP CONSTRAINT IF EXISTS ck_booking_incident_recipient_status;
ALTER TABLE booking_incident_recipient
    ADD CONSTRAINT ck_booking_incident_recipient_status
        CHECK (status IN ('PREPARED', 'QUEUED', 'CANCELLED'));

ALTER TABLE outbound_message
    DROP CONSTRAINT IF EXISTS ck_outbound_message_purpose;
ALTER TABLE outbound_message
    ADD CONSTRAINT ck_outbound_message_purpose CHECK (purpose IN (
        'PAYMENT_LINK','BOOKING_CONFIRMATION','MEETING_LINK','ORDER_STATUS',
        'QUOTE','REMINDER','DELIVERY_STATUS','INCIDENT_NOTICE'
    ));

CREATE UNIQUE INDEX uq_booking_incident_recipient_outbound_message
    ON booking_incident_recipient(outbound_message_id)
    WHERE outbound_message_id IS NOT NULL;
