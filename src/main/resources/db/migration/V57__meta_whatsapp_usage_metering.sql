-- V57: Meter Meta WhatsApp AI replies that are persisted in messaging_message.
-- Generic outbound_message rows were already metered by V41. Conversational
-- assistant replies use messaging_message, so they need an equivalent durable
-- source without inventing provider prices or changing commercial enforcement.

INSERT INTO usage_meter_event (
    business_id, meter_key, quantity, unit, source_type, source_id,
    provider, idempotency_key, occurred_at
)
SELECT
    mc.business_id,
    'OUTBOUND_MESSAGES',
    1,
    'COUNT',
    'MESSAGING_MESSAGE',
    mm.id::text,
    mm.provider,
    'MESSAGING_MESSAGE:' || mm.id::text || ':META_SENT',
    mm.sent_at
FROM messaging_message mm
JOIN messaging_conversation mc ON mc.id = mm.conversation_id
WHERE mm.provider = 'META_WHATSAPP_CLOUD'
  AND mm.provider_message_id IS NOT NULL
  AND mm.sent_at IS NOT NULL
  AND mm.provider_delivery_status IN ('SENT', 'DELIVERED', 'READ')
ON CONFLICT (business_id, idempotency_key) DO NOTHING;

CREATE OR REPLACE FUNCTION record_meta_whatsapp_messaging_usage_meter()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    tenant_business_id UUID;
BEGIN
    IF NEW.provider <> 'META_WHATSAPP_CLOUD'
       OR NEW.provider_message_id IS NULL
       OR NEW.sent_at IS NULL
       OR NEW.provider_delivery_status NOT IN ('SENT', 'DELIVERED', 'READ') THEN
        RETURN NEW;
    END IF;

    SELECT mc.business_id
      INTO tenant_business_id
      FROM messaging_conversation mc
     WHERE mc.id = NEW.conversation_id;

    IF tenant_business_id IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO usage_meter_event (
        business_id, meter_key, quantity, unit, source_type, source_id,
        provider, idempotency_key, occurred_at
    ) VALUES (
        tenant_business_id,
        'OUTBOUND_MESSAGES',
        1,
        'COUNT',
        'MESSAGING_MESSAGE',
        NEW.id::text,
        NEW.provider,
        'MESSAGING_MESSAGE:' || NEW.id::text || ':META_SENT',
        NEW.sent_at
    )
    ON CONFLICT (business_id, idempotency_key) DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_meta_whatsapp_messaging_usage_meter_insert
AFTER INSERT ON messaging_message
FOR EACH ROW
EXECUTE FUNCTION record_meta_whatsapp_messaging_usage_meter();

CREATE TRIGGER trg_meta_whatsapp_messaging_usage_meter_update
AFTER UPDATE OF provider, provider_message_id, provider_delivery_status, sent_at ON messaging_message
FOR EACH ROW
EXECUTE FUNCTION record_meta_whatsapp_messaging_usage_meter();
