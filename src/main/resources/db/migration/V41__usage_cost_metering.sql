-- V41 Usage & Cost Metering
-- Universal, append-only, tenant-scoped ledger for measurable platform usage.
-- Meter keys are data, not industry branches: new capabilities can emit usage
-- without adding restaurant/clinic/etc. conditionals.

CREATE TABLE usage_meter_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no BIGSERIAL NOT NULL UNIQUE,
    business_id UUID NOT NULL,
    meter_key VARCHAR(80) NOT NULL,
    quantity NUMERIC(20,6) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    estimated_cost_usd NUMERIC(18,8),
    actual_cost_usd NUMERIC(18,8),
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(180) NOT NULL,
    provider VARCHAR(60),
    idempotency_key VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_usage_meter_key_non_blank CHECK (btrim(meter_key) <> ''),
    CONSTRAINT ck_usage_meter_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_usage_meter_unit_non_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_usage_meter_estimated_cost_non_negative CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0),
    CONSTRAINT ck_usage_meter_actual_cost_non_negative CHECK (actual_cost_usd IS NULL OR actual_cost_usd >= 0),
    CONSTRAINT ck_usage_meter_source_type_non_blank CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_usage_meter_source_id_non_blank CHECK (btrim(source_id) <> ''),
    CONSTRAINT ck_usage_meter_idempotency_non_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT uq_usage_meter_idempotency UNIQUE (business_id, idempotency_key)
);

CREATE INDEX idx_usage_meter_business_occurred
    ON usage_meter_event(business_id, occurred_at DESC);
CREATE INDEX idx_usage_meter_business_key_occurred
    ON usage_meter_event(business_id, meter_key, occurred_at DESC);
CREATE INDEX idx_usage_meter_business_source
    ON usage_meter_event(business_id, source_type, source_id);

-- Existing terminal calls become the initial authoritative voice usage baseline.
INSERT INTO usage_meter_event (
    business_id, meter_key, quantity, unit, estimated_cost_usd,
    source_type, source_id, provider, idempotency_key, occurred_at
)
SELECT
    cs.business_id,
    'VOICE_SECONDS',
    cs.duration_seconds,
    'SECONDS',
    cs.estimated_total_cost_usd,
    'CALL_SESSION',
    cs.id::text,
    cs.telephony_provider,
    'CALL_SESSION:' || cs.id::text || ':VOICE_SECONDS',
    cs.ended_at
FROM call_session cs
WHERE cs.ended_at IS NOT NULL
  AND cs.duration_seconds IS NOT NULL
ON CONFLICT (business_id, idempotency_key) DO NOTHING;

-- Existing actually-sent outbound messages become the messaging baseline.
INSERT INTO usage_meter_event (
    business_id, meter_key, quantity, unit, source_type, source_id,
    provider, idempotency_key, occurred_at
)
SELECT
    om.business_id,
    'OUTBOUND_MESSAGES',
    1,
    'COUNT',
    'OUTBOUND_MESSAGE',
    om.id::text,
    om.provider,
    'OUTBOUND_MESSAGE:' || om.id::text || ':SENT',
    om.sent_at
FROM outbound_message om
WHERE om.status = 'SENT'
  AND om.sent_at IS NOT NULL
ON CONFLICT (business_id, idempotency_key) DO NOTHING;

CREATE OR REPLACE FUNCTION record_call_usage_meter()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.ended_at IS NULL OR NEW.duration_seconds IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO usage_meter_event (
        business_id, meter_key, quantity, unit, estimated_cost_usd,
        source_type, source_id, provider, idempotency_key, occurred_at
    ) VALUES (
        NEW.business_id,
        'VOICE_SECONDS',
        NEW.duration_seconds,
        'SECONDS',
        NEW.estimated_total_cost_usd,
        'CALL_SESSION',
        NEW.id::text,
        NEW.telephony_provider,
        'CALL_SESSION:' || NEW.id::text || ':VOICE_SECONDS',
        NEW.ended_at
    )
    ON CONFLICT (business_id, idempotency_key) DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_call_usage_meter_insert
AFTER INSERT ON call_session
FOR EACH ROW
EXECUTE FUNCTION record_call_usage_meter();

CREATE TRIGGER trg_call_usage_meter_update
AFTER UPDATE OF ended_at, duration_seconds, estimated_total_cost_usd, telephony_provider ON call_session
FOR EACH ROW
EXECUTE FUNCTION record_call_usage_meter();

CREATE OR REPLACE FUNCTION record_outbound_message_usage_meter()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status <> 'SENT' OR NEW.sent_at IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO usage_meter_event (
        business_id, meter_key, quantity, unit, source_type, source_id,
        provider, idempotency_key, occurred_at
    ) VALUES (
        NEW.business_id,
        'OUTBOUND_MESSAGES',
        1,
        'COUNT',
        'OUTBOUND_MESSAGE',
        NEW.id::text,
        NEW.provider,
        'OUTBOUND_MESSAGE:' || NEW.id::text || ':SENT',
        NEW.sent_at
    )
    ON CONFLICT (business_id, idempotency_key) DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbound_message_usage_meter_insert
AFTER INSERT ON outbound_message
FOR EACH ROW
EXECUTE FUNCTION record_outbound_message_usage_meter();

CREATE TRIGGER trg_outbound_message_usage_meter_update
AFTER UPDATE OF status, sent_at, provider ON outbound_message
FOR EACH ROW
EXECUTE FUNCTION record_outbound_message_usage_meter();

CREATE OR REPLACE FUNCTION reject_usage_meter_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'usage_meter_event is append-only';
END;
$$;

CREATE TRIGGER trg_usage_meter_event_immutable
BEFORE UPDATE OR DELETE ON usage_meter_event
FOR EACH ROW
EXECUTE FUNCTION reject_usage_meter_event_mutation();

-- V40 only hardened tables that existed at migration time. V41 therefore
-- installs RLS explicitly for this new tenant-owned ledger.
GRANT SELECT, INSERT ON TABLE public.usage_meter_event TO helvoca_runtime;
GRANT USAGE, SELECT ON SEQUENCE public.usage_meter_event_sequence_no_seq TO helvoca_runtime;
ALTER TABLE public.usage_meter_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usage_meter_event FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.usage_meter_event;
DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.usage_meter_event TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE usage_meter_event IS
    'V41 append-only tenant usage and cost ledger; billing enforcement consumes this ledger but does not own it.';