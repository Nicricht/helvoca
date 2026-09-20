-- V56: provider delivery tracking for conversational WhatsApp replies.
-- The existing messaging_message row already stores the persisted inbound message
-- and its reply_text. These columns attach the provider delivery lifecycle of that reply.

ALTER TABLE messaging_message
    ADD COLUMN provider VARCHAR(40),
    ADD COLUMN provider_message_id VARCHAR(180),
    ADD COLUMN provider_delivery_status VARCHAR(20),
    ADD COLUMN failure_code VARCHAR(80),
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN delivery_updated_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN read_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_messaging_message_provider_message
    ON messaging_message(provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;
