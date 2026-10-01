# V82 Commercial Economics Guard

## Purpose

V82 completes the economics layer already started by V41 usage metering and V42 commercial entitlements.

It does **not** create a second billing source of truth:

- V41 `usage_meter_event` remains the authoritative period usage ledger.
- V42 `commercial_plan` and `commercial_plan_entitlement` remain the source of plan limits and overage prices.
- V82 adds immutable voice cost dimensions, provider/model-aware cost estimation, customer consumption warnings, an emergency runaway ceiling, and a private platform economics view.

## Truth boundaries

All cost and margin figures are **estimates** unless an `actual_cost_usd` value has been reconciled into the usage ledger.

An estimated commercial value is not a collected payment. It is calculated from the current fixed plan reference plus priced voice overage for active/past-due subscriptions.

Custom-priced plans such as Enterprise are never treated as known revenue merely because the catalog contains a reference price. Their commercial value remains unknown until a separate authoritative contract/payment source exists.

If `HELVOCA_COST_USD_TO_CLP` is zero or missing, USD cost remains visible but CLP cost and gross margin remain unavailable. The system does not invent an exchange rate.

## Provider/model-aware voice cost

Completed calls preserve:

- telephony provider;
- AI provider;
- AI model;
- estimated telephony cost;
- estimated AI cost;
- estimated total cost.

Rate precedence is:

1. AI model-specific rate;
2. AI provider-specific rate;
3. legacy generic AI rate.

Telephony cost uses a provider-specific rate when available and otherwise the legacy generic telephony rate.

Deployment variables:

```text
HELVOCA_COST_USD_TO_CLP
HELVOCA_TELEPHONY_COST_PER_MINUTE_USD
HELVOCA_AI_COST_PER_MINUTE_USD
HELVOCA_TWILIO_COST_PER_MINUTE_USD
HELVOCA_GEMINI_COST_PER_MINUTE_USD
HELVOCA_GEMINI_3_8_LIVE_COST_PER_MINUTE_USD
HELVOCA_OPENAI_LIVE_COST_PER_MINUTE_USD
HELVOCA_GPT_LIVE_1_COST_PER_MINUTE_USD
HELVOCA_OPENAI_REALTIME_COST_PER_MINUTE_USD
HELVOCA_GPT_REALTIME_2_1_COST_PER_MINUTE_USD
```

Provider prices are operational configuration because vendors can change them independently of a RecepVoz release.

## Customer consumption states

`GET /api/v1/usage/status` is tenant-scoped and BUSINESS_ADMIN-only.

Voice usage state:

| Included usage | State |
|---:|---|
| < 70% | NORMAL |
| >= 70% and < 90% | NOTICE |
| >= 90% and < 100% | WARNING |
| = 100% | LIMIT |
| > 100% | OVERAGE |

The account screen shows the applicable warning and the estimated CLP overage based on the plan's authoritative V42 overage rule.

RecepVoz internal provider cost and gross margin are never exposed on the customer account screen.

### Customer API redaction

`GET /api/v1/usage/summary` returns only meter key, unit, quantity and event count to BUSINESS_OWNER / BUSINESS_ADMIN. Internal `estimated_cost_usd` and `actual_cost_usd` remain available only inside backend/platform economics and are not serialized to tenant clients.

## Emergency safety ceiling

Ordinary voice allowance remains a soft commercial entitlement with priced overage.

V82 adds `VOICE_SAFETY_SECONDS` as a separate hard entitlement at 10x the included voice allowance. Its purpose is to stop pathological or runaway consumption, not to replace the customer's normal overage policy.

Current seeded safety ceilings:

| Plan | Included | Safety ceiling |
|---|---:|---:|
| Emprende | 100 min | 1,000 min |
| Negocio | 250 min | 2,500 min |
| Pro | 500 min | 5,000 min |
| Enterprise | 1,000 min | 10,000 min |

When the hard safety entitlement is exceeded, the existing commercial entitlement service makes voice service unavailable and call admission fails closed.

## Internal platform economics

`GET /api/v1/platform/economics` is PLATFORM_ADMIN-only and executes under the platform SYSTEM database scope.

It reports:

- fixed-plan estimated commercial value;
- estimated priced overage;
- estimated platform cost in USD;
- CLP cost when a conversion rate is configured;
- estimated gross margin and percentage when both commercial value and CLP cost are known;
- per-business usage/cost/margin;
- AI provider/model call count, minutes and estimated cost.

The platform UI labels these values as estimates. It must not describe them as collected revenue, settled provider invoices or accounting profit.

## Data migration

V82 extends `usage_meter_event` with immutable snapshots:

- `telephony_provider`;
- `ai_provider`;
- `ai_model`;
- `telephony_cost_usd`;
- `ai_cost_usd`.

Historical call-derived ledger rows are backfilled from `call_session` during the forward migration. The append-only trigger is restored immediately afterward.

## External-effect safety

This feature reads commercial data and estimates cost. It does not:

- place calls;
- send messages;
- charge a customer;
- activate a provider;
- change a customer's plan;
- purchase provider credit.
