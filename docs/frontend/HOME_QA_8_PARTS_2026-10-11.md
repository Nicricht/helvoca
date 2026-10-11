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
| 2 | Inicio session/onboarding and dashboard lifecycle: missing token, 401/403/503, network interruption, loading, fail-closed malformed payloads, retry and next-step mappings | **IMPLEMENTED; CI PENDING** |
| 3 | Human attention: request/handoff actions, success/failure, confirmation cancellation, duplicate clicks, 401/403/409/503, stale/refetch, least privilege, malformed success payload | **IMPLEMENTED; CI PENDING** |
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

- Current tracked milestone: **Parts 1–3 implemented**, exact certification pending.
- New branch is deliberately kept separate from `main`. Do not merge until Part 8 evidence satisfies the repository quality contract.
- Resume next with **Part 4** after inspecting the exact PR HEAD and CI. Parts 1-3 must not be repeated; unresolved CI issues take priority.
- After every phase update this document and draft PR with exact HEAD/evidence before responding to the user.

## Part 2 checkpoint: Inicio identity / onboarding / dashboard resilience

**Scope edited:** `frontend/src/pages/Home/HomePage.tsx`, `HomePage.module.css`, new `e2e/react-home-resilience.spec.js`. Shared app frame and API clients unchanged.

- **Regression suite:** 16 standalone fixture-driven tests plus 8 parameterized onboarding next-step variants (24 browser cases total). Existing `e2e/react-home.spec.js` coverage remains intact. Includes missing token, auth 403/503, malformed success identity, onboarding 401/403/503/malformed, pending gate, all 8 settings routes, dashboard 403/503/network outage, retries and no business data before validation.
- **Implementation:** fail closed with visible accessible error and retry rather than spinner/blank page on invalid authentication/onboarding; on dashboard failure provide a local retry. Only Home-local CSS updated.
- **Side effect boundary:** all new network requests are intercepted with local Playwright mocks. No calls, messages, payments or production writes.
- **Preexisting tests:** main React Home assertions and project Fast/Full Gate still required. Part 7 will enforce 100% *measured* original-source coverage for affected TSX, including the new branches; no unsupported 100% claim is made here.
- **Verification:** JavaScript syntax was parsed successfully through V8 (`new Function`) after test edits; full Playwright and TypeScript build must be proven via GitHub Actions on the exact final HEAD. A static/parse check is not a test pass. **RED was not directly observed in a completed runner**; tests were added before fixing the Home failures.
- **CI:** workflows for intermediate Part 2 commits were pending or in progress when authored. Consult latest final-HEAD run before completing or merging.
- **No merge:** PR remains draft until complete eight-part QA and coverage evidence.

## Part 3 checkpoint: actionable request/handoff integrity

**Risk:** HIGH for explicit human mutation, authorization and false-clear data. **FRAME CHANGE:** NO.

**New test suite:** `e2e/react-home-attention-hardening.spec.js`, **20** deterministic Playwright cases:
- no mutations at first render/refetch; no provider calls;
- authorized request OPEN -> IN_PROGRESS PATCH;
- request resolution confirmation and cancellation without mutation;
- HTTP 409/403 request failures preserve authoritative state and allow controlled retry;
- handoff take/resolve failures (HTTP 503) preserve open cases and successful retry;
- handoff resolution confirmation cancellation without POST;
- duplicated same-tick progress/close click yields at most one mutation;
- stale visible items are non-actionable while attention query refetches;
- failed GET 503/403 never becomes "Todo bajo control"; retry recovery;
- staff without attention role cannot read/mutate;
- operator lacking `REQUESTS_MANAGE` cannot mutate requests; a separate handoff can be acknowledged by operator per existing `HumanHandoffController` role authorization;
- request PATCH 401 clears session, does not persist a fabricated success;
- malformed HTTP 200 `null`/entry from attention API fails closed and can recover upon valid refetch.

**Smallest behavioral fixes:** `HomeAttentionRows.tsx` uses a synchronous `useRef` mutation lock alongside React state; actionable buttons are disabled during authoritative refetch. `getHumanAttention` validates the HTTP 200 response is an array of actionable records before confirming the queue state. **No Java, database, global AppShell, payment, provider, or production mutation.**

**Evidence:** New JavaScript Playwright source passed V8 parse through `new Function` before commit. This is *not* test execution. New source-code branches in `HomeAttentionRows.tsx` and `features/operations/api.ts` must reach the project-required **100% measured frontend coverage** in Part 7. Browser suite and Full Gate are awaiting exact-HEAD CI. Any observed failure requires investigation and fresh commit/certification.

**Previous Part 2 CI:** exact SHA `4fb4666002f1f1d064e14fbcca0207fc3d37fba5`, workflow `38113544485`: Fast Gate, Golden Journey and system-integration jobs were green when last checked; backend/full browser `test` and Inventory coverage were still in progress. It cannot certify later HEAD commits.

**Safety and hold:** Mock-only browser calls, no live calls/messages/payments. PR stays Draft; no merge until all eight phases, Fast/Full Gate and exact-HEAD code/source/invariant evidence are green.
