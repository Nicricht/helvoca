# Inicio / Home QA certification: eight-part execution

**Repository:** `Nicricht/helvoca`  
**Base main SHA:** `7d19fe92f5f771f7f7ba3d34648414eb7520dc41`  
**Branch:** `test/home-quality-eight-parts-20261011`  
**Risk:** HIGH (role/security-sensitive human action, tenant isolation, operational truth).  
**FRAME CHANGE:** NO. No global shell or unrelated screen redesign.  
**Safety:** mock/sandbox browser routes and PostgreSQL Testcontainers only. No live Twilio calls, WhatsApp delivery, payment, production database mutation, or provider credentials.

## Release contract

Per `AGENTS.md` and `docs/engineering/QA_POLICY.md`:
- Affected Java executable lines, branches and methods: **100% differential** when applicable.
- Affected frontend executable statements/lines, branches and functions: **100% measured**, plus 100% meaningful controls/state transitions and release-critical journeys.
- Affected persistence, isolation, and concurrency invariants: 100% mapped to **real PostgreSQL** evidence. Browser mocks cannot prove PostgreSQL behavior.
- CI on the **exact final commit**, not historical green checks. Full Gate must pass before merge, and merged main must be reverified.
- Do not relax thresholds or claim an unmeasured 100%.

## Phases

| Part | Scope and acceptance criteria | Status |
| --- | --- | --- |
| 1 | Inspect current main, map coverage, repair stale special-certification Playwright paths, add fail-closed path-existence regression assertions, create draft PR and checkpoint | **IMPLEMENTED; CI NOT YET CERTIFIED** |
| 2 | Inicio session/onboarding and dashboard lifecycle: 401/403/500/503, loading, empty/error/refetch, next-step mapping, missing/partial responses | PENDING |
| 3 | Human attention: request/handoff actions, success/failure, confirmation cancellation, duplicate clicks, 401/403/409, stale/refetch, least privilege | PENDING |
| 4 | Backend / PostgreSQL: dashboard count and daily-window correctness, tenant boundary, attention deduplication/order/LIMIT, concurrency and request lifecycle where applicable | PENDING |
| 5 | Commercial analytics: currency aggregation and formatting, null/missing/invalid values, daily/7/30-day query switching, no fabricated ROI or zero, timezone boundaries | PENDING |
| 6 | Responsive/accessibility and non-side-effect browser behavior: keyboard, focus, live states, reduced motion, multiple viewports, no provider/payment requests | PENDING |
| 7 | Frontend original-source coverage measurement, per-file 100% statement/line/branch/function gate for affected scope; complete interaction-to-test traceability | PENDING |
| 8 | Fast Gate, Full Gate, adverse review, exact-HEAD CI and coverage report, merge only if green, exact-main verification and evidence checkpoint | PENDING |

## Part 1 findings (reproducible RED evidence)

Default `main` did not contain:
- `e2e/first-user-ux-v2.spec.js`
- `e2e/home-operational.spec.js`
- `e2e/inventory.spec.js`

Yet `scripts/ci/first-fictional-customer-week.sh` and `scripts/ci/adversarial-hardware-store-week.sh` referenced these paths; the direct GitHub contents requests returned 404. Such Playwright commands cannot select a non-existent test file.

The branch replaces those paths with the **existing** suites `e2e/frontend-ux.spec.js`, `e2e/react-settings-core.spec.js`, `e2e/react-home.spec.js` and `e2e/react-inventory.spec.js`. The existing `e2e/operations-internal.spec.js` remains included. Both Java script-contract tests now require all referenced suites to be real files, making a repeat regression visible.

**Verification pending:** Java tests, Playwright E2E, PostgreSQL suite, source coverage and exact-HEAD CI must run. Implementation is not certification.

## Known baseline / risk matrix

| Behavior | Existing automated evidence | Missing or unproven |
| --- | --- | --- |
| Authenticated owner and operator Home | `e2e/react-home.spec.js` | Auth/backend failure and partial-data edge cases |
| 401 session expiry | `e2e/react-home.spec.js` | Access-policy combinations and operational partial failures |
| Onboarding progress and navigation | `e2e/react-home.spec.js`, `e2e/react-settings-core.spec.js` | HTTP error/retry, all nextStep mappings and unresolved status |
| Operational metrics / recent events | `e2e/react-home.spec.js`, `OperationsDashboardServiceTest` | Real data boundary/timezone and robustness cases |
| Attention view, actions and deduplication | `e2e/react-home.spec.js`, `RequestLifecycleAttentionIntegrationTest` | HTTP failure/409, cancellation, double clicks, role boundaries, SQL 100-limit and ordering |
| Sales analytics period, currencies, partial error | `e2e/react-home.spec.js` | Local formatting and all meaningful currency / null / time scenarios |
| Motion and 3 responsive viewports | `e2e/dashboard-motion.spec.js`, `e2e/react-home.spec.js` | Broader keyboard/focus/accessibility controls |
| React source code coverage | Existing Istanbul instrumentation targets **Inventory** only | Add dedicated Home instrumentation and report/enforcement |
| No external actions from page load | `e2e/react-home.spec.js` | No real provider side effects during expanded suites |

## Continuity checkpoint

- Current tracked milestone: **Part 1 implemented**, exact certification pending.
- New branch is deliberately kept separate from `main`. Do not merge until Part 8 evidence satisfies the repository quality contract.
- Resume next with **Part 2**, first reconstruct repository -> PR -> branch HEAD -> latest CI -> this document; do not restart or redo Part 1.
- After every phase update this document and draft PR with exact HEAD/evidence before responding to the user.
