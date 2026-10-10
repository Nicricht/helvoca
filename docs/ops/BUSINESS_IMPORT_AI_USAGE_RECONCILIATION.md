# Helvoca | OpenAI document-import usage evidence and cost reconciliation

**2026-10-10 status: CAPTURE PREPARED / PROVIDER SPEND NOT AUTHORIZED.**

Paid AI document import stays default-OFF; Railway presently has none of the operator authorization/budget variables configured. No real calls to OpenAI are needed to verify this implementation. This applies to paid image/PDF import only, not the voice receptionist.

## Durable provider evidence

Migration `V98__business_import_ai_provider_usage_evidence.sql` creates the append-only, tenant-RLS-protected table `business_import_ai_provider_usage_event`. It contains:
- `business_id`, randomized `attempt_id` and `phase` (`STARTED`, `RESPONSE`, `UNCERTAIN`)
- Request/response identifiers and requested/returned model, HTTP status
- Provider `usage.input_tokens`, `usage.input_tokens_details.cached_tokens`, `usage.output_tokens`
- `estimated_cost_usd` **nullable**. There is NO `actual_cost_usd` column because an individual response is not a provider invoice
- Timestamp. No document bytes, filenames, prompt/response content, secret or personal message data

`STARTED` is committed **before** the external call. `RESPONSE` is committed **before** interpreting returned catalog data; a network failure attempts an `UNCERTAIN` receipt. Database failure before `STARTED` prevents a provider request. Later database failure blocks successful completion and leaves `STARTED` for investigation. A missing `usage`, malformed response or timeout is **unknown**, not zero. The database guarantees uniqueness for each tenant/attempt/phase and RLS tenant boundaries.

### Pricing configuration (all default OFF)

These environment variables can be used only after a human validates the current official model tariffs and invoice treatment:

| Name | Default | Purpose |
| --- | --- | --- |
| `HELVOCA_BUSINESS_IMPORT_AI_PRICED_MODEL` | empty | Exact provider returned model/version eligible for a tariff |
| `HELVOCA_BUSINESS_IMPORT_AI_INPUT_USD_PER_MILLION` | `0` | Non-cached input USD per 1M |
| `HELVOCA_BUSINESS_IMPORT_AI_CACHED_USD_PER_MILLION` | `0` | Cached input USD per 1M |
| `HELVOCA_BUSINESS_IMPORT_AI_OUTPUT_USD_PER_MILLION` | `0` | Output USD per 1M |

A cost estimate is **NULL unless** the configured model matches both requested and provider-returned models and all three rates are positive. When `cached_tokens` is omitted, it is stored as `NULL` and the conservative estimate treats all input as **non-cached**, not as a free cache hit. The calculation is:
```
estimate USD = ((input_tokens - cached_input_tokens) * input_rate
                + cached_input_tokens * cached_input_rate
                + output_tokens * output_rate) / 1,000,000
```
It uses BigDecimal and rounds to 8 decimal places. It is **not** actual billed dollars, does not include every surcharge, PDF processing nuance, committed capacity tier or other billable tool charges. Config is deliberately zero until pricing is verified. See official live documentation: https://developers.openai.com/api/docs/pricing and https://developers.openai.com/api/docs/models/gpt-4.1-mini .

## Invoice-level reconciliation, not invented per-request charges

OpenAI's [organization Costs API](https://platform.openai.com/docs/api-reference/usage) provides aggregated daily costs, requiring a separately authorized **admin key**. It is not queried automatically in this release, and no admin credential is provisioned or read.

For the first explicitly authorized pilot, allocate a **dedicated OpenAI project and API key for import analysis**, segregated from voice and development. Each day, an authorized finance operator should:
1. Export the project's OpenAI organization Costs API results with `group_by=project_id` and UTC day boundaries. Record the source data, USD currency and invoice/report date securely **outside tenant logs**.
2. Export the matching usage SQL below. Treat records in `STARTED` with no terminal event, `UNCERTAIN`, null token totals, and missing estimate as **unreconciled**. Do not silently drop them.
3. Compare *project-wide* invoiced USD against *project-wide* estimated USD, explicitly noting the number of missing/unknown responses, adjustments, separate tools, retry behavior and time cutoffs. Do **not** allocate an aggregate billing difference to individual clients without auditable evidence.
4. Freeze commercial rollout if the invoice exceeds the reserved budget, if unknown provider usage accumulates, if model prices change, or if the Costs API buckets do not correspond to the dedicated project.
5. Confirm provider-level spending boundaries/alerts and company-approved pilot budget before any live paid file test.

Safe, read-only operational query (requires authorized system DB access, never exposed publicly):
```sql
SELECT date_trunc('day', recorded_at AT TIME ZONE 'UTC') AS utc_day,
       count(*) FILTER (WHERE phase = 'STARTED') AS attempted,
       count(*) FILTER (WHERE phase = 'RESPONSE') AS responded,
       count(*) FILTER (WHERE phase = 'UNCERTAIN') AS uncertain,
       count(*) FILTER (WHERE phase = 'RESPONSE' AND input_tokens IS NULL) AS missing_usage,
       count(*) FILTER (WHERE phase = 'RESPONSE' AND estimated_cost_usd IS NULL) AS unknown_estimate,
       sum(estimated_cost_usd) FILTER (WHERE phase = 'RESPONSE') AS estimated_usd
FROM public.business_import_ai_provider_usage_event
GROUP BY 1 ORDER BY 1 DESC;
```

**Limitations:** This phase captures provider usage evidence and a documented aggregate reconciliation protocol; it does NOT establish real invoice amounts or validate provider costs in an account without an approved API admin key and pilot budget. A true automated reconciliation service, billing alerts and paid entitlement enforcement are intentionally deferred until the operator approves those accesses (tracking issue #773).

## Release invariants
- CI: 100% differential original-source Java line, branch, method coverage, real PostgreSQL evidence/RLS/idempotency test, zero real paid OpenAI requests
- Existing invoice payment, call billing and voice costs not modified
- Production paid document AI import remains OFF and all reservation budgets zero
- Merge/deploy only with exact-head CI + Railway Wait for CI; **never enable this feature as a side effect of merging this release**
