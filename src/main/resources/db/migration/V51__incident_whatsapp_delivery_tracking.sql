-- V51 Incident WhatsApp delivery tracking
-- Stores provider delivery progress without changing the authoritative outbound
-- lifecycle used for metering. Real delivery remains guarded by existing flags.

ALTER TABLE outbound_message
    ADD COLUMN provider_delivery_status VARCHAR(20),
    ADD COLUMN delivery_updated_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN read_at TIMESTAMPTZ,
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outbound_message
    ADD CONSTRAINT ck_outbound_message_provider_delivery_status
        CHECK (provider_delivery_status IS NULL OR provider_delivery_status IN (
            'QUEUED','SENT','DELIVERED','READ','FAILED','UNDELIVERED','CANCELED'
        )),
    ADD CONSTRAINT ck_outbound_message_retry_count
        CHECK (retry_count >= 0 AND retry_count <= 20);

CREATE INDEX idx_outbound_message_provider_sid
    ON outbound_message(provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;
