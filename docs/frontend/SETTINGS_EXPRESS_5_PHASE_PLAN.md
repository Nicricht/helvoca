# Settings Express: five-phase delivery checkpoint

Baseline main: `0b694ca11f2b21ef022964f2480c675d92544f5d`
Branch: `feat/settings-express-phased-20261009`
Risk: HIGH for paid provider execution, data overwrite, permissions and multi-tenancy.
FRAME CHANGE: NO. Scope: Settings content/importer, not the shared app shell.

## Five phases

1. **Cost guard (this checkpoint):** API key alone does not authorize a paid image/PDF import. Default flag `HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` preserves spreadsheet processing, marks image/PDF results `AI_DISABLED`, and avoids provider calls. Flag-on is an explicit operations choice. This is NOT a full per-tenant spending budget.
2. **Independent saves:** business, profile, AI agent, services, hours and knowledge. Eliminate unrelated list replacement and handle partial errors.
3. **Cost-efficient multimodal import:** optimize image/PDF, reuse local structured parsing and source comparisons; benchmark accuracy/latency/cost and enforce tenant budgets BEFORE enabling paid provider in production.
4. **Settings Express UX:** import → review → prepare, advanced editing preserved, mobile/a11y/motion validation. Do not change shared frame.
5. **Full QA/release:** exact-HEAD Fast/Full gates; 100% applicable coverage, real PostgreSQL invariants, provider boundaries and tenant isolation; merge/deploy and exact-main production verification only after gates.

## Phase 1 acceptance

- Configured API key with default flag OFF must yield `AI_DISABLED` with no HTTP request.
- An explicit flag ON with absent API key must yield `AI_UNAVAILABLE` and no HTTP request.
- Explicit flag ON with mocked configured API key must exercise only the mocked HTTP analysis path.
- Supported spreadsheets and explicit preview → review → apply separation must remain unaffected.
- No new paid provider, database, migration or Railway service.

## Evidence / current blocker

Three focused guard tests added. The editing runtime has no Maven executable; exact-head GitHub Actions is authoritative. Until verified, **CI NOT CERTIFIED**.
The current Settings screen still uses a cross-section save that may replace unrelated lists: **Phase 2 pending**. No production setting was changed, no paid call triggered, and this PR remains Draft and unmerged through the phased work.

Next: implement tested independent saving in the existing UI/API and update this checkpoint on exact HEAD.
