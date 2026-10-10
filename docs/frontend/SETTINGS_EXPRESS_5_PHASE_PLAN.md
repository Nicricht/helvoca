# Settings Express: five-phase delivery checkpoint

Baseline main: `0b694ca11f2b21ef022964f2480c675d92544f5d`
Branch: `feat/settings-express-phased-20261009`
Risk: HIGH for paid provider execution, data overwrite, permissions and multi-tenancy.
FRAME CHANGE: NO. Scope: Settings content/importer, not the shared app shell.

## Five phases

1. **Cost guard (this checkpoint):** API key alone does not authorize a paid image/PDF import. Default flag `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` preserves spreadsheet processing, marks image/PDF results `AI_DISABLED`, and avoids provider calls. Flag-on is an explicit operations choice. This is NOT a full per-tenant spending budget.
2. **Independent saves (implemented, awaiting exact-HEAD certification):** isolated core business PATCH and profile PUT, agent PUT, individual service/knowledge POST/PATCH/DELETE, hours-only PUT. Each acknowledged mutation updates the retry baseline, preserving other unsaved sections; failed source reads reject writes. Existing incomplete business/service/hours fields do not block unrelated saves.
3. **Cost-efficient multimodal import:** optimize image/PDF, reuse local structured parsing and source comparisons; benchmark accuracy/latency/cost and enforce tenant budgets BEFORE enabling paid provider in production.
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

## Evidence / current blocker

Phase 1: three focused guard tests. Phase 2: browser regressions for isolated saves, partial failures, service/knowledge and no unrelated writes. The editing runtime has no Maven executable or authenticated repository checkout; **the exact HEAD GitHub Actions run is the certification authority**. Do not claim green while CI is running.
The Settings global cross-section save was replaced with section-specific mutations on the feature branch. No production setting was changed, no paid call triggered, and this PR remains Draft and unmerged through the phased work.

Next after Phase 2 validation: upon user «continúa», Phase 3 multimodal import optimization, per-tenant budgets and extraction benchmarking. Preserve the existing cost guard until budgets are proven.
