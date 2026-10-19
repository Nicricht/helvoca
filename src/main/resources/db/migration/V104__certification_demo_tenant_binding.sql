ALTER TABLE twilio_certification_command
    ADD COLUMN business_id UUID REFERENCES business(id);

CREATE INDEX ix_twilio_certification_demo_pending
    ON twilio_certification_command (requested_at)
    WHERE status = 'PENDING' AND business_id IS NOT NULL;
