# Paid document-import AI: commercial cost protection (October 2026)

## Status
**NOT enabled in production.** This project is deployed with `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` and all quota/budget inputs set to zero by default. Having an OpenAI API key or paying for Railway hosting does **not** authorize new document analysis charges. CSV/XLS/XLSX imports do not call a paid model.

This release adds a **conservative shared reservation gate** to protect against unrestricted uploads. It is **not a financial ledger, exact token-cost metering or provider-enforced spending cap**. Actual provider charges depend on model pricing, image/PDF expansion, usage tokens and billable policies, and may differ from configured reservations.

## Operator-only configuration

| Environment variable | Default | Meaning |
| --- | ---: | --- |
| `HELVOCA_BUSINESS_IMPORT_AI_ENABLED` | `false` | Master off switch for photo/PDF paid analysis |
| `HELVOCA_BUSINESS_IMPORT_AI_MAX_ATTEMPTS_PER_30_DAYS` | `0` | Per-tenant attempt count ceiling |
| `HELVOCA_BUSINESS_IMPORT_AI_RESERVED_CENTS_PER_ATTEMPT` | `0` | Conservative manually validated estimated USD cents to reserve per provider attempt |
| `HELVOCA_BUSINESS_IMPORT_AI_MAX_TENANT_RESERVED_CENTS_PER_30_DAYS` | `0` | Tenant ceiling in **reserved** estimated USD cents |
| `HELVOCA_BUSINESS_IMPORT_AI_MAX_GLOBAL_RESERVED_CENTS_PER_30_DAYS` | `0` | Shared global ceiling in **reserved** estimated USD cents across **all** tenants |
| `HELVOCA_BUSINESS_IMPORT_AI_MAX_FILES` | `3` | Maximum distinct paid files per request |
| `HELVOCA_BUSINESS_IMPORT_AI_MAX_BYTES` | `4194304` | Aggregate paid upload bytes, default 4 MiB |
| `HELVOCA_BUSINESS_IMPORT_AI_MODEL` | `gpt-4.1-mini` | Model specifically for document-import analysis |

The Java guard uses the shared, PostgreSQL-backed `api_rate_limit_bucket` as fixed 30-day counters. The number of allowed cost-reserved attempts per tenant is `floor(tenant_cents / cents_per_attempt)`; globally it is `floor(global_cents / cents_per_attempt)`. **Every precondition must be positive and explicitly configured.** Reservations are *never refunded*, including when the provider times out or rejects the request. If PostgreSQL or tenant identity lookup fails, the preview returns `AI_BUDGET_EXCEEDED` and no provider request is sent. Rejections at later checks may consume capacity in prior buckets, conservatively under-allocating rather than risking an overrun.

Example **only**, not a recommendation or an actual price quote: if operations separately measured and verified that a request should reserve USD $0.50, set `reserved_cents_per_attempt=50`, `tenant_reserved_cents=500` and `global_reserved_cents=5000`. Combined with tenant attempts=8, the tenant gets at most 8 attempts even though its reserved-cost ceiling permits 10. The aggregate can approve at most 100 cost-reserved requests in the fixed window before other tenant limits. Actual provider invoices may be higher or lower.

### Critical caveats

- The `request_count` limiter tracks one **estimated fixed reservation per call**, not real billable token costs. Changing model, reservation or pricing configuration during an active 30-day window may invalidate estimates. Disable the master switch and reconcile before changing them.
- Counters are shared and atomic, but tenant quota and global limit are separate sequential SQL operations. No distributed transaction makes them a single all-or-nothing charge. If a later reservation is denied, earlier capacity may be consumed. This is deliberately conservative.
- No guarantee of a literal USD invoice hard stop: a provider can charge more than the forecast. Set the provider's own organization/project spending alert and hard cap (where supported), use provider billing reconciliation, and observe real image/PDF token usage before enabling.
- Do not store card details, prompts, file contents, raw AI credentials or provider responses in analytics/logs.
- This configuration is global for all tenants, **not yet based on individual subscribed plan tiers**, and does not implement paid overages, billing or refunds.
- Do **not** toggle import AI on as a side effect of Railway subscription payment or deployment. Document analysis is separate from the AI that answers voice calls.

## Go/no-go checks for first real paid pilot

1. Re-enable and verify Railway **Wait for CI** on `helvoca-api` (tracking issue #771).
2. Measure actual request usage and invoices against a varied small sample of PDFs and images using an explicit owner-approved spending allowance. Select a reservation in cents that defensibly bounds tested input distributions; limit file/page complexity if necessary.
3. Implement and certify an actual billed-USD reconciliation/audit trail and usage alerts, as well as global/provider-level rate and spending boundaries.
4. Validate tenant plan entitlements, per-business allowances, opt-in and user-visible exhaustion/retry messages.
5. Run exact-HEAD CI with 100% differential line, branch and method coverage; real PostgreSQL contention and fail-closed cases; real provider must remain mocked in automated tests.
6. Roll out to one consenting trial business with a small operational budget, manual monitoring and one-click emergency-off. Only after validation, consider self-service general availability.

No provider requests, production environment variable changes, price assumptions or extra Railway services are part of this PR.

## Gemini opt-in for business import (PR #777)

Dedicated Gemini document import works through the existing authenticated preview endpoint.
It extracts products, FAQ and business hours for human review. Extras and promotions
are NOT automatically published or silently converted into catalog items.
The API adapter defaults to OFF; the presence of a live voice API key does not enable it.

Use a separate import-only key, never the existing GEMINI_API_KEY used by voice:
- HELVOCA_BUSINESS_IMPORT_AI_PROVIDER=gemini
- HELVOCA_BUSINESS_IMPORT_GEMINI_API_KEY: dedicated private key, not committed
- HELVOCA_BUSINESS_IMPORT_GEMINI_MODEL=gemini-3.5-flash-lite
- HELVOCA_BUSINESS_IMPORT_AI_ENABLED remains false until explicit cost authorization.

Global/tenant shared AI budget, subscription entitlement, max attempts and reserved
cents still require positive operator-configured values. A zero budget blocks requests.
The provider token receipt records Gemini promptTokenCount/candidatesTokenCount, not
document contents, and does NOT prove actual charges are zero. Never enable paid
requests without verified provider quotas and billing restrictions.

Real provider calls from the Java server and tenant-safe E2E are still pending.
The verified PowerShell Gemini request was a separate offline test of 11 products.
Do not deploy or merge this draft until exact-head CI passes and the operator
explicitly approves the separate project key and provider use.
