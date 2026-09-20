# V41 Usage & Cost Metering

## Goal

V41 adds a universal tenant-scoped usage ledger between operational execution and future plan/billing enforcement. It records measurable consumption without embedding industry-specific logic.

## Invariants

- `usage_meter_event` is append-only in PostgreSQL. UPDATE and DELETE are rejected.
- Every row is tenant-scoped by `business_id` and protected by forced PostgreSQL RLS.
- Runtime inserts are fail-closed under the tenant context installed by V40.
- `(business_id, idempotency_key)` is unique, so retries and concurrent workers cannot double count the same usage event.
- Meter keys and units are extensible data. The domain does not branch on restaurant, clinic, vet, or any other industry.
- Estimated provider cost and actual provider cost are distinct fields. Metering never pretends an estimate is an invoiced amount.
- Metering does not activate providers, calls, WhatsApp delivery, payments, calendars, or other external effects.

## Initial authoritative sources

### Voice

When a `call_session` has an `ended_at` timestamp and a duration, PostgreSQL records one `VOICE_SECONDS` event. The event snapshots the call's estimated total cost and telephony provider. A migration baseline imports existing terminal calls.

### Outbound messaging

When an `outbound_message` reaches `SENT`, PostgreSQL records one `OUTBOUND_MESSAGES` event with quantity `1`. A migration baseline imports existing sent messages. No provider price is invented when the application does not know it.

Database triggers are used for these authoritative sources so a future adapter or retry path cannot silently bypass usage capture. Meta WhatsApp conversational AI replies persisted through `messaging_message` are also recorded as `OUTBOUND_MESSAGES` once Meta accepts the send and a provider message id is durable; later `delivered`/`read` callbacks do not double count.

## Application API

`UsageMeterService.record(...)` is the generic domain entry point for future capabilities. It derives `business_id` from the authenticated tenant instead of accepting a caller-supplied tenant id and uses the database idempotency boundary.

`GET /api/v1/usage/summary?from=<instant>&to=<instant>` is restricted to `BUSINESS_ADMIN` and returns totals grouped by meter key and unit, including estimated and actual costs when available.

## Relationship to V42

V41 measures. It does not decide plan eligibility, quotas, overages, invoice amounts, or payment collection. Those concerns belong to the next block, plans/limits/billing, which can consume this ledger without coupling operational channels to commercial policy.
