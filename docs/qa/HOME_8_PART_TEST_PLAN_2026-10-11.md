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

## Exit conditions

For each phase: tests are committed to the QA branch, assertions check observable behavior (not only implementation strings), no production side effects occur, and failures are fixed or documented. The final certification in Phase 8 requires a successful verified release gate.
