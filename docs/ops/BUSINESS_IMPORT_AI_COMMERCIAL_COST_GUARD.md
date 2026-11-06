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

## Free CSV/TSV FAQ and weekly-hours review · PR #862 (2026-11-06)

This is an unpaid, frontend-only increment of [Issue #776](https://github.com/Nicricht/helvoca/issues/776). It does not enable, implement or call the paid image/PDF Gemini/OpenAI path.

- The existing authenticated /api/v1/onboarding/import/preview remains unchanged. The browser also reads ONLY small UTF-8 .csv/.tsv files (up to 1 MiB each) and proposes explicit FAQ pairs (pregunta/respuesta) and weekly opening/closing intervals (dia/apertura/cierre). Header synonyms allowed. No inferred time, policy, availability or answer. UTF-8 BOM, quoted separators and tabs handled; invalid rows warned and omitted.
- Every candidate carries original filename and source row. At most 100 suggestions per batch; at most 500 file rows. Conflicts for the same question/day are excluded with visible warning, not guessed. Proposals are unselected by default.
- The canonical React page mounts FreeSetupReview only once a preview exists. The business administrator may select, deselect and edit, then click Aprobar sugerencias seleccionadas. No business data saved on preview. Existing BUSINESS_ADMIN role guard retained on both page and server endpoints.
- Approved FAQ are compared against existing stored knowledge; same-title FAQ are never overwritten. Approved hours merge with existing weekly schedule, leaving all unselected days untouched. Invalid intervals, duplicate days and duplicate questions fail closed before writes. FAQ and hours must be approved in separate batches due to independent REST transaction boundaries.
- All mutations reuse existing tenant-authenticated /api/v1/knowledge and /api/v1/business/hours APIs. No new SQL/schema/API/provider credentials, calls, orders, reservations, messages or payments. A network failure mid-FAQ batch can leave previously created FAQ; UI warns to verify existing state before retry and repeated same-title entries are skipped.
- Scope limitation: no free FAQ/hour extraction from XLS/XLSX, PDF or images. Existing Excel catalogue, gated paid import and existing business-facts review remain unchanged. No claim of full-document auto-configuration or verified ten-minute onboarding.
- QA gate: [PR #862](https://github.com/Nicricht/helvoca/pull/862) must complete exact-head Fast/Full, TypeScript and E2E. PR remains DRAFT; no merge/deploy without certification. Automated test time is not human setup time.

**CI checkpoint (2026-11-06):** The repository's required pull-request CI targets `main`; for exact-HEAD certification the draft PR #862 uses that base temporarily, with a matching pre-change receipt. After the required checks conclude, it can be re-stacked over PR #858. This affects only the PR comparison base, never the protected branch, business data or deployment.
