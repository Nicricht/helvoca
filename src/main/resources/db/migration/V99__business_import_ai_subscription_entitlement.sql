-- V99. Reuse the V42 commercial catalogue and V41 immutable meter.
-- No unapproved commercial allowances are advertised or granted.
-- Operations may change limits only after an explicit commercial decision.
INSERT INTO public.commercial_plan_entitlement (
    plan_code, entitlement_key, kind, meter_key, limit_value, unit,
    hard_limit, overage_unit_size, overage_price_clp
)
SELECT code, 'AI_IMPORT_REQUESTS', 'USAGE', 'AI_IMPORT_REQUESTS', 0,
       'REQUESTS', TRUE, NULL, NULL
  FROM public.commercial_plan
ON CONFLICT (plan_code, entitlement_key) DO NOTHING;

COMMENT ON COLUMN public.commercial_plan_entitlement.limit_value IS
    'Subscription usage limit; AI_IMPORT_REQUESTS defaults to 0 for all plans until approved by the operator. No paid import allowance is implicit.';
