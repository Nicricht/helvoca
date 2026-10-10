# Settings Express: five-phase delivery checkpoint

Baseline main: `0b694ca11f2b21ef022964f2480c675d92544f5d`
Branch: `feat/settings-express-phased-20261009`
Risk: HIGH for paid provider execution, data overwrite, permissions and multi-tenancy.
FRAME CHANGE: NO. Scope: Settings content/importer, not the shared app shell.

## Five phases

1. **Cost guard (this checkpoint):** API key alone does not authorize a paid image/PDF import. Default flag `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` preserves spreadsheet processing, marks image/PDF results `AI_DISABLED`, and avoids provider calls. Flag-on is an explicit operations choice. This is NOT a full per-tenant spending budget.
2. **Independent saves (implemented, awaiting exact-HEAD certification):** isolated core business PATCH and profile PUT, agent PUT, individual service/knowledge POST/PATCH/DELETE, hours-only PUT. Each acknowledged mutation updates the retry baseline, preserving other unsaved sections; failed source reads reject writes. Existing incomplete business/service/hours fields do not block unrelated saves.
3. **Cost-efficient multimodal import (implemented, verification pending):** paid API calls require both global flag and atomic tenant allowance from existing PostgreSQL limiter, default 0 attempts. Limit paid bytes, media count and generated tokens; dedupe identical image/PDF files within a batch, reject unreadable/unsupported inputs before provider. Spreadsheet processing remains free of paid AI API. No new database migration.
4. **Settings Express UX:** import → review → prepare, advanced editing preserved, mobile/a11y/motion validation. Do not change shared frame.
5. **Full QA/release:** exact-HEAD Fast/Full gates; 100% applicable coverage, real PostgreSQL invariants, provider boundaries and tenant isolation; merge/deploy and exact-main production verification only after gates.

## Phase 1 acceptance

- Configured API key with default flag OFF must yield `AI_DISABLED` with no HTTP request.
- An explicit flag ON with absent API key must yield `AI_UNAVAILABLE` and no HTTP request.
- Explicit flag ON with mocked configured API key must exercise only the mocked HTTP analysis path.
- Supported spreadsheets and explicit preview → review → apply separation must remain unaffected.
- No new paid provider, database, migration or Railway service.

## Phase 2 acceptance and implementation

- Active-tab save only; no bulk `/api/v1/onboarding/setup` from Settings.
- Saving the core business name never mutates services, knowledge, hours, profile, AI agent or channels.
- Profile edit and receptionist edit remain independent, even if no services/hours exist.
- Service and knowledge edits use per-item CRUD; soft delete affects only the selected item. Historical inactive records are not reactivated on hydration.
- Hours replace only the hours collection via its dedicated endpoint, with same-day time normalization.
- Drafts in non-active sections survive tab navigation. Partial business success (core succeeds, profile fails) only retries the pending profile.
- A failed query for the active section blocks writes rather than treating missing data as an empty authoritative list.
- Role checks for save match the server's BUSINESS_ADMIN requirement.
- Regression spec: `e2e/react-settings-independent-save.spec.js` (plus updated existing React Settings contracts).
- Remaining risk to certify during phase 5: provider/race/ambiguous network errors, browser differential branch coverage and cross-tenant provider/data contracts.

## Phase 3 implementation and cost truth

- Global `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` and `HELVOCA_BUSINESS_IMPORT_AI_MAX_ATTEMPTS_PER_30_DAYS=0` **both** default to deny paid analysis. A provider API key alone can never enable it.
- A tenant's allowed attempts use atomic PostgreSQL buckets `business-import-ai:<business UUID>`, a fixed 30-day period, and are independent between tenants/instances. Rejected attempts never contact OpenAI. Allowed attempts count even if provider fails, to resist unbounded retries.
- Default per-provider attempt: at most three unique image/PDF files, four MiB combined, and `max_output_tokens=2000`; import model configurable separately, default `gpt-4.1-mini`.
- SHA-256 content detection skips identical images/PDF in the same batch. Only JPG/JPEG, PNG, WEBP and PDF are accepted for paid semantic analysis. Unreadable inputs are never uploaded.
- Mock-provider tests cover permitted/blocked/error paths and batch limits; real PostgreSQL concurrency test confirms two different business IDs cannot share a budget.
- **Cost distinction:** this is a **request-volume quota**, NOT an exact USD billing budget and NOT a verified real-world price benchmark. Image and PDF input token charges vary, as do supplier rates. Actual invoice reconciliation, persistent cross-upload cache, low-cost local PDF text extraction, and real-customer accuracy/cost benchmarks remain pending. Do not enable paid imports commercially until that work is validated. No provider, real communication or production mutation was invoked by our tests.
- **Legacy CI blocker:** unrelated Inventory source coverage remains 96.67% statements / 96.69% branches for InventoryPage and 100% required outside historical PR #768. No exception to 100% is authorized here. Exact-HEAD CI must pass before merge. Phase 2 browser regressions were updated to validate independent saves rather than the retired bulk save.

## Evidence / current blocker

Phase 1: three focused guard tests. Phase 2: browser regressions for isolated saves, partial failures, service/knowledge and no unrelated writes. The editing runtime has no Maven executable or authenticated repository checkout; **the exact HEAD GitHub Actions run is the certification authority**. Do not claim green while CI is running.
The Settings global cross-section save was replaced with section-specific mutations on the feature branch. No production setting was changed, no paid call triggered, and this PR remains Draft and unmerged through the phased work.

Next on user «continúa»: Phase 4 Settings Express UX (Import → Review → Prepare), user-facing quota errors, document/photos upload, mobile/a11y and advanced forms retained. In Phase 5 finish high-risk QA, inventory 100% gate blocker and exact-main certification. Do not merge while blocked.
