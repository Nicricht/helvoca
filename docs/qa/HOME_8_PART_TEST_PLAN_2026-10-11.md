# Inicio QA certification | 8-part execution plan

Repository: `Nicricht/helvoca`. Canonical route: `/app` (React Home).
Baseline inspected: `main@7d19fe92f5f771f7f7ba3d34648414eb7520dc41` (2026-10-11).
This plan is a test-execution checklist, not a claim of completed certification.
Do not call live phone, WhatsApp or payment providers; use safe mocks/fixtures for browser tests.

## Contract and baseline

- Frontend implementation: `frontend/src/pages/Home/HomePage.tsx` and `HomeAttentionRows.tsx`.
- Relevant existing suites: `e2e/react-home.spec.js`, `e2e/dashboard-motion.spec.js`, frontend UI and backend operations/onboarding tests.
- Product evidence: `docs/frontend/SCREEN_BLUEPRINTS.md` and `docs/frontend/VISUAL_ACCEPTANCE_MATRIX.md`.
- Use behavior in the current React implementation for release contracts where older blueprint copy or legacy navigation differs.
- Verify CI through GitHub Actions. The Java differential PR gate requires 100% lines, branches and methods on changed Java code; a green gate alone is not proof of 100% global feature coverage.
- Do not merge until every requested phase and the mandatory checks have been evaluated.

## Phases

1. **Fail closed and recoverability**: onboarding and operations failures must never silently render an empty or fictitious home. Add Playwright outage and explicit retry coverage; no mutations during page load.
2. **Authentication, roles and tenant boundaries**: unauthenticated/expired/forbidden outcomes; no unauthorized reads or actions; do not send client-controlled tenant identity.
3. **Onboarding contract**: four configured states, next-step routing, progress, incomplete owner, operator bypass, invalid/missing payload and recovery states.
4. **Daily operational truth**: calls, bookings, duration rounding, in-progress calls, timezone and day-boundaries, empty/invalid records, latest activity ordering.
5. **Commercial value truth**: selected periods and request races, mixed currencies, source attribution, zero/null/invalid values and partial analytics failure.
6. **Human attention workflows**: deduplication, request/handoff transitions, explicit confirmation, idempotent clicks, permission restrictions, failure and refresh.
7. **Responsive, motion and accessibility**: 1440/768/390 plus edge widths; keyboard, semantics, focus, reduced motion, no horizontal overflow.
8. **Real-stack and release verification**: React + Spring Boot + PostgreSQL assertions, tenant isolation, deterministic suite, regression checks, Java diff coverage gate where applicable, passing Full Gate and a final evidence report.

## Phase 1 implementation

- [x] Audit existing Home contracts and relevant tests on pinned `main`.
- [x] Ensure a failed onboarding read displays an explicit error and a retry action instead of a blank area.
- [x] Make an operations read failure explicitly recoverable without displaying fictitious metrics.
- [x] Add Playwright checks for onboarding fail-closed, transient onboarding recovery, and transient operations recovery.
- [ ] Verify the PR test job and browser E2E results; check actual execution before marking this done.

## Phase 2 implementation

- [x] Compare React Home access decisions with Spring's actual operations controller roles.
- [x] Stop operational dashboard, attention and sales requests for roles without server-side operations access.
- [x] Render an explicit restricted Home state instead of attempting protected reads.
- [x] E2E: anonymous entry never makes protected Home reads; STAFF is restricted even if forged permission labels suggest operations access.
- [x] E2E: fake `businessId` query string is not forwarded by Home.
- [x] Real-stack E2E: register an independent test tenant and confirm its dashboard and attention cannot be influenced by tenant A's `businessId`.
- [ ] Verify the full PR CI, including the latest head, prior to merging.

## Phase 3 implementation

- [x] Verify canonical backend readiness semantics: business profile and active phone are required, services/hours depend on business type, knowledge and human transfer remain optional.
- [x] Cover the four onboarding steps, correct 0/25/50/75% progress and no premature Home operational API requests.
- [x] Verify legacy next-step aliases still navigate to canonical React Settings.
- [x] Verify that a product business not needing reservations or services can be ready despite those optional steps remaining false.
- [x] Validate onboarding API payloads at the UI boundary; missing readiness or non-boolean step flags fail closed with a retry action.
- [ ] Confirm exact HEAD browser tests, backend tests, real-stack integration and all required CI jobs.

## Phase 4 implementation

- [x] Match frontend count labels to backend truth: logged calls can include failed/unanswered calls; new bookings can have multiple sources. Avoid claiming all calls were answered or all bookings were AI-created.
- [x] Exercise in-progress call chip and resolution labels with safe Playwright mocks.
- [x] Exercise mixed recent requests/calls ordering and business-local display time, including invalid timezone fallback.
- [x] Exercise no-call/booking-present state and exact duration rounding boundaries (0/1/29/59/60/89/90/3600 seconds).
- [x] Add backend Mockito contract tests for per-tenant local-day query boundaries, simulator/certification filters, failed call counts, booking exclusion, read-model details and null aggregates.
- [ ] Confirm all Full Gate jobs on the **exact latest PR HEAD** including Playwright and PostgreSQL before certifying Phase 4.

## Phase 5 implementation

- [x] Verify server semantics for completed paid orders, completed paid bookings, source attribution, currency isolation and null aggregate behavior.
- [x] Normalize currency codes and reject negative/nonfinite/invalid attributed values before display.
- [x] Show missing/partial attribution transparently instead of inventing $0 when transactions exist without a safe single-currency breakdown.
- [x] Preserve true zero for empty/zero-attribution contexts, and sum comparable CLP amounts only within the same currency.
- [x] Test mixed CLP/USD separate display without converting; verify one-sided partial attribution.
- [x] Test rapid 7→30→1 days query races; reject stale response overwrites using actual React Query query key behavior.
- [x] Test selected-period HTTP 503 isolation, switching back to healthy period without losing daily operation metrics.
- [ ] Verify the exact latest head Fast Gate, Java coverage, Playwright and full real-stack CI. Fix any recorded failures before promoting.

## Phase 6 implementation

- [x] Confirm server read model scopes each row to tenant and deduplicates active handoff/request pairs; PostgreSQL already verifies handoff priority and request resurfacing after handoff-only resolution.
- [x] Protect same-tick duplicate clicks with a synchronous ref lock and disable manual refresh during mutations.
- [x] Isolate in-progress and confirmation identifiers by `kind:id` instead of raw ID to avoid cross-kind confirmations.
- [x] Extend Playwright mock to simulate temporary attention-read and mutation failures without committing fake server-side transitions.
- [x] Reject malformed attention API payloads at the shared API boundary; only an authoritative [] can mean an empty queue. Add null/object/partial-row and legitimate-empty browser tests.
- [x] Add Playwright checks for cancellation, failure/retry, duplicate submissions, independent handoff/request lifecycle and readonly request permissions.
- [ ] Verify latest exact SHA Full Gate and any failing browser cases before merge.

## Change safety

- No existing database schema or production credentials touched.
- Browser tests mock API responses; real-stack test uses disposable CI Postgres.
- The restricted Home state follows actual server controller authorization and avoids interpreting frontend-only permission claims as tenant or role authority.
- This phase adds no production API calls beyond reads and no provider calls.

## Exit conditions

For each phase: tests are committed to the QA branch, assertions check observable behavior (not only implementation strings), no production side effects occur, and failures are fixed or documented. The final certification in Phase 8 requires a successful verified release gate.
