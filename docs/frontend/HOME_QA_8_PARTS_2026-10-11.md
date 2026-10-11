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
| 4 | Backend / PostgreSQL: tenant-isolated dashboard metrics/calls/costs, Santiago/Auckland day boundaries, attention deduplication/order/LIMIT 100, RLS, concurrency and request lifecycle | **CERTIFIED exact SHA c6f2ebe8, CI 38114322289 GREEN** |
| 5 | Commercial analytics: currency aggregation and formatting, null/missing/invalid values, daily/7/30-day query switching, no fabricated ROI or zero, timezone boundaries | **IMPLEMENTED; exact-HEAD CI PENDING** |
| 6 | Responsive/accessibility and non-side-effect browser behavior: keyboard, focus, live states, reduced motion, multiple viewports, no provider/payment requests | **IMPLEMENTED; exact-HEAD CI PENDING** |
| 7 | Frontend original-source coverage measurement, per-file 100% statement/line/branch/function gate for affected scope; complete interaction-to-test traceability | **INSTRUMENTATION AND GATE IMPLEMENTED; exact-HEAD measured outcome PENDING** |
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

- Current tracked milestone: **Parts 4 and 5 certified on exact previous SHAs; Part 6 Full Gate pending; Part 7 gate implemented, exact-HEAD result pending**.
- New branch is deliberately kept separate from `main`. Do not merge until Part 8 evidence satisfies the repository quality contract.
- Resume next with **Part 8** ONLY after resolving Parts 6–7 RED and certifying exact-HEAD original-source 100% plus cross-layer gates. Never merge a pending or red branch.
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

## Part 4 checkpoint: actual PostgreSQL invariants and dashboard

**Scope:** Java **test-only** additions to `RequestLifecycleAttentionIntegrationTest.java` (4 tests) and `OperationsDashboardServiceTest.java` (2 tests). No production Java or SQL migrations modified. Testcontainers `postgres:16-alpine`, Flyway migrations, explicit scoped tenant execution. No external provider calls.

### Behavioral matrix (all must pass in real CI)

1. Tenant A vs tenant B: active handoff overrides paired request; each visible exactly once; resolved handoff restores still-open request; resolved request disappears.
2. Tenant RLS: direct SQL `SELECT` under A cannot read B's handoff or business_request even without service WHERE filters; B can read its own. Cross-tenant handoff update fails.
3. List bounds: 105 stored A handoffs and an out-of-tenant newer B handoff; `LIMIT 100` gives newest A 100, in order, without duplicates or foreign row; no incorrect "historical total" assertion.
4. Two real concurrent transactions from same tenant progress OPEN -> IN_PROGRESS; returned status correct; one durable audit transition, one actionable item, no lost update.
5. Real dashboard with tenant-linked synthetic test calls: Auckland local-day `[dayStart, nextDayStart)`, failure count, duration and cost, boundary exclusion, certification/simulator exclusion, no cross-tenant calls or requests, real saved request lifecycle count changes, independent tenant B view.
6. Unit: all dashboard repositories use the same tenant and local-day window, distinct failed statuses, 10-item recent-call page descending, zero defaults and absence of SQL calls without authenticated tenant.

### Debugging evidence

- Earlier Part 4 Fast Gate from intermediate commit (workflow `38114172089`) failed **for the intended test-fixture reason**: PostgreSQL's V93 `fk_human_handoff_operation_tenant` rejected fake `operation_id` UUID references (2 new test errors; all other focused tests passed). This was a genuine observed RED, not a product failure.
- Fixed the foreign handoff to reference the real foreign request's persisted `operation_id`.
- Fixed the 105+1 LIMIT fixtures by using unique `source_reference_id` values with nullable `operation_id`, respecting the composite FK and active-handoff uniqueness.
- Read V4 call_session schema and added tenant-linked **synthetic phone_number fixtures**, because `phone_number_id` is NOT NULL and constrained to the same business. No real Twilio provision or message/call.
- **GREEN VERIFIED for Part 4:** exact commit `c6f2ebe8ea02fce95c58efd5f1eb67e9f953db5e`, workflow `38114322289` concluded **success**. All 28 jobs completed; 6 success and 22 conditional skips; Fast Gate, Golden Journey, test (backend Java/JaCoCo, differential 100%, browser E2E), System Integration and Inventory coverage completed successfully. Later Part 5 HEAD requires new certification.
- Part 2 and Part 3 Fast Gate/Golden Journey successfully completed. The long-running Full Gate browser jobs were not yet fully certified at the last check.
- **Next:** inspect this document plus `PR #780`, exact HEAD and Fast/Full CI results; if red resolve root cause first, else ask the user for Part 5.

**Safety:** test data only, Testcontainers ephemeral PostgreSQL, mock voice-provider *rows* without external requests. No production DB, live phone, WhatsApp, payment or deployment. The PR stays Draft until Part 8 exact HEAD/merged-main certification.

## Part 5 checkpoint: sales attribution and timezone

**Implementation:** Hardened frontend `attributedTotals` and `ValueGenerated` in `frontend/src/pages/Home/HomePage.tsx`: no invented `$0` when monetary attribution is null/unknown; reject malformed types, negative/non-finite amounts and invalid currency code shapes; disclose partial totals without implying that a known component proves a full sum. Verified zero still displays zero. No changes to backend production code.

**New Playwright coverage:** Nine additional deterministic cases inside `e2e/react-home.spec.js`, using local API fixtures only: null versus verified zero, malformed negative/string/currency, partial evidence notice, same/mixed currency aggregation without cross-currency addition or unauthorised ROI, 1/7/30 period API source switching, and operational availability while commercial data is incomplete.

**Backend coverage:** One focused Java unit test in `SalesAnalyticsServiceTest.java` looping 9 combinations of three windows (1/7/30 days) and three zones (America/Santiago, Pacific/Auckland, UTC). Captures Instant boundaries sent to tenant-scoped repositories and asserts current/previous half-open local-calendar windows, daily series size, empty/unknown revenue not synthesized, and no order/line reads with no payment candidates. Existing SalesAnalyticsService and BookingRevenue tests continue to cover same/mixed currencies, cancelled orders, verified bookings and dedupe.

**Explicit certification limitations:** 100% measured React source statement/line/branch/function coverage remains the dedicated Part 7 gate, not inferred from nine passing E2E cases. Part 5 browser/backend test execution requires exact-HEAD CI; do not merge or call this certified until green. Previous Part 4 green is distinct from Part 5 HEAD.

**No side effects:** E2E API routes are mock-only, no real phone call, Twilio, WhatsApp, payment, production DB mutation or deployment.

## Part 6 checkpoint: accessible Home, keyboard, reduced motion and reflow

**Risk:** MEDIUM for presentation and keyboard access; HIGH for unsafe writes, false confirmations or provider effects. **FRAME CHANGE:** NO. Production edits are strictly inside Home's local page/components/styles.

**Part 5 CI prerequisite:** Commit `d2cb0883f3d1509fc1d3316676264b3ed20c7a33` workflow `38115261252`. Fast Gate, Golden Journey, system integration, and inventory coverage jobs were green at last inspection; long-running full browser `test` job was still IN PROGRESS. This is NOT a completed green certification, and any final failure must be resolved before promoting Part 5/6.

**New end-to-end cases:** **14** new browser test invocations appended to `e2e/react-home.spec.js` (10 singular cases, 4 viewport variants); uses current mock-only `bootHome` fixture:
1. Tab + Space toggles sales period, validates `aria-pressed`, authenticated GET with `days=30`, actual keyboard focus outline.
2. History's Enter-open and Space-close, `aria-expanded`, `aria-controls` and controlled `hidden` region without mutation.
3. Keyboard confirmation cancellation results in **zero close requests**.
4. Keyboard confirmation of explicit status close produces exactly one authorized PATCH after prior progress action.
5. Incomplete onboarding `progressbar` value 25/100, keyboard link navigation target and visible focus, protected operations not fetched.
6. Attention error exposes `role=alert`, Enter-triggered retry, still no mutation.
7. Semantic headings, five labelled operational regions, and inaccessible decorative animation backgrounds.
8. Motion preference reduce disables Home CSS animations/transitions (computed style and `matchMedia`).
9. Pending commercial data has `aria-busy`, announces `status`, does not hide operational summaries; recovers when mocked response resolves.
10. Navigating read-only periods, updating inbox GET, opening history never POSTs, calls Twilio or submits payments.
11–14. Reflow and 24px minimum period control bounds at **320x640, 360x740, 1024x768, 1920x1080**, with no document/body horizontal overflow.

**Smallest production fixes:**
- `HomePage.module.css`: explicit keyboard `:focus-visible` outline for sales periods, onboarding link and hero action; scoped reduced-motion CSS override including background layers and transitions.
- `HomePage.tsx`: history button declares `aria-controls="home-history-panel"` and an always addressable, conditionally hidden controlled region.
- `HomeAttentionRows.tsx`: destructive inline confirmation receives a labelled accessible `group` with case context.

**Verification limits:** The new JavaScript test file passed V8 syntax parsing with `new Function`; this does **not** prove browser tests passed. New exact-HEAD Fast Gate, browser E2E, mobile reflow, and source coverage measurements still need GitHub Actions evidence. Part 7 must enforce 100% executable source statement/line/branch/function coverage for all changed frontend behaviors, with complete interaction traceability. Never infer this from Playwright case counts alone.

**Safety:** Existing mocked localhost browser routes only. No real phone calls, WhatsApp, Twilio, outbound messages, Stripe/provider payments, Railway deployment or production SQL writes. PR remains Draft and unmerged. Await user confirmation after this phase; do not automatically start Part 7.

## Part 7 checkpoint: actual Home original-source coverage

**Part 5 prerequisite certified:** GitHub workflow `38115261252` for exact commit `d2cb0883f3d1509fc1d3316676264b3ed20c7a33` completed `success`; Fast Gate, full backend/browser, Golden Journey, system integration, Inventory coverage passed. This verifies Part 5 only, not a later HEAD.

**Part 6 previous gate:** GitHub workflow `38115721621` for exact commit `7bd630aa91496f8f22b74f827dbc26bcdc59014a` had Fast Gate/Golden Journey/integration green, browser Full Gate still in progress at Part 7 entry. Check before allowing later release.

**Scoped new original-source instrumentation:**
- `frontend/vite.config.ts` has explicit `VITE_HOME_COVERAGE=true` Istanbul build (separate from `VITE_COVERAGE=true` Inventory), instrumenting only three affected sources: `HomePage.tsx`, `HomeAttentionRows.tsx`, `features/operations/api.ts`. Plain/prod builds are not instrumented and cannot accidentally combine both coverage modes.
- `e2e/home-coverage-fixture.js` captures real per-test `window.__coverage__` via Chromium, archives counters across page navigations and records success/failure for each test. The fixture was enabled in all four suites: `react-home.spec.js`, `react-home-resilience.spec.js`, `react-home-attention-hardening.spec.js`, `dashboard-motion.spec.js`.
- `scripts/ci/check-home-frontend-coverage.cjs` checks independent Playwright discovery total (all four suites) against all recorded tests; rejects missing suites, lost snapshots, source archive errors, missing embedded source maps or any of the three original files absent after Vite/Istanbul remapping. **Per-file 100%** for statements, lines, branches and functions; no exception/threshold relaxation. Artifacts: JSON + Markdown per source with uncovered original lines/functions/branches; job log prints actionable gaps on RED.
- `.github/workflows/ci.yml` adds independent `home-frontend-coverage` PR job, with explicit instrumented build, all four Playwright suites, strict remapping gate and artifacts on failure. Existing Inventory gate is not edited or relaxed.
- `docs/frontend/HOME_INTERACTIONS_COVERAGE_MATRIX.md` records the complete known affected Home control/state and cross-layer acceptance inventory, mapped to browser/Java/PostgreSQL tests, and explicitly flags reused support-panel inventory as a hold.

**Observed strict-gate RED + root-cause correction:** Interim workflow `38116203213` on `4e01e04c` ran **88/88 Playwright cases successfully** and failed `home-frontend-coverage`, correctly refusing to certify: **0 instrumented source maps, 0 coverage snapshots**, 87 recorded vs 88 discovered tests. This was instrumentation/collection failure, not a passing 100% result. The Home Istanbul configuration used `requireEnv: true` (internally gated by **VITE_COVERAGE**, the Inventory-only variable), even though the new isolated build sets **VITE_HOME_COVERAGE**. Corrected Home plugin to `requireEnv: false` *inside the already conditional Home-only plugin creation*, retaining Inventory's unchanged config and production build isolation. The static CSS test in `dashboard-motion.spec.js` now requests the common `page` fixture so all 88+ discovered tests produce a truthful record, even when the CSS-only test has no instrumented snapshot. Exact-HEAD rerun must show real remapped percentages; do not treat this fix as certification.

**Additional functional mapping:** Added malformed payload branch cases for attention `null`, scalar, wrong kind, missing ID, nonstring status, unfamiliar statuses, invalid case timestamp/title and 100-case recent sample. Added history panel tests for explicit request progress, read-only role, audit filter/CSV download using **fully mocked local** APIs with zero provider calls. Their exact SHA E2E result is pending.

**Limits / honesty:** Parser validation of JS/CJS is not a measured browser pass. Actual original-source coverage percentages require the **new exact HEAD** workflow. The gate is expected to fail if any true gap remains; only add meaningful tests or smallest verified fixes to close it. Do not artificially suppress source counters or treat a test pass as percentage coverage. No merge or Part 8 certification while any required check is pending/red.

**Safety:** All browser API traffic is local mocked Playwright; no real calls, provider messages, billing, production DB, production deployments or remote side-effect tests.
