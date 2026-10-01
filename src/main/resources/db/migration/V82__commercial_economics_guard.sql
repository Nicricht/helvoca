-- V82 Commercial economics guard
-- Adds provider/model cost dimensions to the append-only usage ledger and
-- introduces a deliberately high emergency voice ceiling. Normal plan limits
-- remain soft and continue to use priced overage.

ALTER TABLE usage_meter_event
    ADD COLUMN telephony_provider VARCHAR(60),
    ADD COLUMN ai_provider VARCHAR(60),
    ADD COLUMN ai_model VARCHAR(120),
    ADD COLUMN telephony_cost_usd NUMERIC(18,8),
    ADD COLUMN ai_cost_usd NUMERIC(18,8);

ALTER TABLE usage_meter_event
    ADD CONSTRAINT ck_usage_meter_telephony_cost_non_negative
        CHECK (telephony_cost_usd IS NULL OR telephony_cost_usd >= 0),
    ADD CONSTRAINT ck_usage_meter_ai_cost_non_negative
        CHECK (ai_cost_usd IS NULL OR ai_cost_usd >= 0);

-- The V41 append-only guard must be temporarily removed only for this
-- forward migration so historical call events can receive immutable snapshot
-- dimensions that already exist on call_session.
DROP TRIGGER IF EXISTS trg_usage_meter_event_immutable ON usage_meter_event;

UPDATE usage_meter_event ume
   SET telephony_provider = cs.telephony_provider,
       ai_provider = cs.ai_provider,
       ai_model = cs.ai_model,
       telephony_cost_usd = cs.estimated_telephony_cost_usd,
       ai_cost_usd = cs.estimated_ai_cost_usd
  FROM call_session cs
 WHERE ume.source_type = 'CALL_SESSION'
   AND ume.source_id = cs.id::text;

CREATE TRIGGER trg_usage_meter_event_immutable
BEFORE UPDATE OR DELETE ON usage_meter_event
FOR EACH ROW
EXECUTE FUNCTION reject_usage_meter_event_mutation();

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
        source_type, source_id, provider, idempotency_key, occurred_at,
        telephony_provider, ai_provider, ai_model,
        telephony_cost_usd, ai_cost_usd
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
        NEW.ended_at,
        NEW.telephony_provider,
        NEW.ai_provider,
        NEW.ai_model,
        NEW.estimated_telephony_cost_usd,
        NEW.estimated_ai_cost_usd
    )
    ON CONFLICT (business_id, idempotency_key) DO NOTHING;

    RETURN NEW;
END;
$$;

-- Emergency runaway protection. These limits are intentionally 10x the
-- ordinary included voice allowance so normal overage remains billable and
-- usable while pathological consumption still fails closed.
INSERT INTO commercial_plan_entitlement(
    plan_code, entitlement_key, kind, meter_key, limit_value, unit,
    hard_limit, overage_unit_size, overage_price_clp
) VALUES
    ('BASIC', 'VOICE_SAFETY_SECONDS', 'USAGE', 'VOICE_SECONDS', 60000, 'SECONDS', TRUE, NULL, NULL),
    ('PRO', 'VOICE_SAFETY_SECONDS', 'USAGE', 'VOICE_SECONDS', 150000, 'SECONDS', TRUE, NULL, NULL),
    ('BUSINESS', 'VOICE_SAFETY_SECONDS', 'USAGE', 'VOICE_SECONDS', 300000, 'SECONDS', TRUE, NULL, NULL),
    ('ENTERPRISE', 'VOICE_SAFETY_SECONDS', 'USAGE', 'VOICE_SECONDS', 600000, 'SECONDS', TRUE, NULL, NULL)
ON CONFLICT (plan_code, entitlement_key) DO NOTHING;

COMMENT ON COLUMN usage_meter_event.telephony_provider IS
    'Immutable telephony provider snapshot for the metered event when known.';
COMMENT ON COLUMN usage_meter_event.ai_provider IS
    'Immutable AI voice provider snapshot for the metered event when known.';
COMMENT ON COLUMN usage_meter_event.ai_model IS
    'Immutable AI model snapshot for the metered event when known.';
COMMENT ON COLUMN usage_meter_event.telephony_cost_usd IS
    'Estimated telephony component of the metered event cost.';
COMMENT ON COLUMN usage_meter_event.ai_cost_usd IS
    'Estimated AI component of the metered event cost.';
