# Inicio interaction-to-test coverage matrix (PR #780, Part 7)

**Status:** MAPPED, but not yet certified at 100%. This is an acceptance inventory,
not a substitute for instrumented original-source or a passing exact-HEAD CI run.
**Scope:** Inicio / Home only, including its embedded `HomeAttentionRows` and
commercial value summary. Expanded `OperationsSupportPanel` is reused by other
routes; its mutations/export controls require their own existing suite evidence
before a complete interaction-coverage claim.

| Surface/control or meaningful transition | Observable outcome required | Automated proof |
| --- | --- | --- |
| Missing/expired auth, invalid identity | No protected requests; accessible refusal or redirect | `react-home-resilience.spec.js` (auth 401/403/503, null identity, retries), `react-home.spec.js` (401) |
| Business onboarding: incomplete steps | Accessible progress and next-step link; no metrics before ready | `react-home.spec.js` (onboarding, unknown/phone mapping), `react-home-resilience.spec.js` (eight nextStep values, pending and failures) |
| Onboarding keyboard focus and semantic status | Tab/focus visible, correct progressbar, settings destination | `react-home.spec.js` (Part 6 onboarding keyboard) |
| Home overview operational totals | Business-specific calls, bookings, daily status; no invented empty activity | `react-home.spec.js` (ready and empty); `OperationsDashboardServiceTest.java`, `RequestLifecycleAttentionIntegrationTest.java` |
| Commercial period buttons Hoy/7/30 | Query parameter changes, `aria-pressed`, keyboard Space/Tab | `react-home.spec.js` (period switching, Part 5 period source, Part 6 keyboard) |
| Commercial loading/failure and recovery | Busy/status or local error, operational summary stays accessible | `react-home.spec.js` (Part 6 aria-busy and 503 partial failure) |
| Commercial value: unknown/partial/zero/invalid | Never invent zero/ROI/currency exchange; label partial | `react-home.spec.js` (nine Part 5 cases); `SalesAnalyticsServiceTest.java` (1/7/30 × timezone) |
| Attention read: API 403/503/malformed 200 | Fail closed with alert; never false 'all clear'; retry | `react-home-attention-hardening.spec.js` (403/503/null/invalid/retry) |
| Attention refresh while stale | Disable actions until authoritative refresh finishes | `react-home-attention-hardening.spec.js` (stale/refetch) |
| Attention refresh via keyboard on error | Retry GET without any mutations | `react-home.spec.js` (Part 6 alert/retry) |
| Request OPEN → IN_PROGRESS | Exactly one scoped PATCH; updated authoritative UI | `react-home-attention-hardening.spec.js` (request status and double-tick) |
| Request IN_PROGRESS → RESOLVED | Explicit confirmation, exactly one mutation | `react-home.spec.js` (Part 6 Enter), `react-home-attention-hardening.spec.js` (resolve) |
| Request close Cancel | Dismiss without PATCH, keyboard Space | `react-home.spec.js` (Part 6 keyboard cancel), `react-home-attention-hardening.spec.js` |
| Request PATCH 401/403/409 | Invalid sessions blocked; state not fabricated; retry if appropriate | `react-home-attention-hardening.spec.js` |
| Handoff OPEN → ACKNOWLEDGED | Explicit authorized POST | `react-home.spec.js` and `react-home-attention-hardening.spec.js` |
| Handoff ACKNOWLEDGED/ASSIGNED → RESOLVED | Confirmation, cancellation and 503 retry | `react-home-attention-hardening.spec.js` |
| No REQUESTS_MANAGE or no attention role | No request mutation, no unauthorized attention reads | `react-home.spec.js`; `react-home-attention-hardening.spec.js` |
| History disclosure Enter/Space | `aria-controls`, `aria-expanded` and controlled content visibility | `react-home.spec.js` (Part 6 history keyboard), `react-home.spec.js` (history secondary) |
| Keyboard outlines and semantic landmarks | Visible focus, labelled status/regions, non-semantic ambient decoration | `react-home.spec.js` (Part 6 focus and landmark cases) |
| Reduced motion | No spinning/animated Home atmosphere when requested | `dashboard-motion.spec.js`, `react-home.spec.js` (Part 6 computed CSS) |
| Viewport adaptation | 320/360/390/768/1024/1440/1920 px without horizontal overflow | `react-home.spec.js` (seven responsive scenarios) |
| Read-only opening/navigation/refresh | No external Twilio, WhatsApp, payments or mutating APIs | `react-home.spec.js` (no side effects), `react-home-attention-hardening.spec.js` (no mutation on load) |
| Tenant PostgreSQL RLS and attention LIMIT 100 | No cross-business items, newest 100, concurrent request transition one audit event | `RequestLifecycleAttentionIntegrationTest.java` (real Testcontainers PostgreSQL) |

## Instrumentation and exact CI contract

- Build with `VITE_HOME_COVERAGE=true` using Vite's Istanbul plugin for
  `src/pages/Home/HomePage.tsx`,
  `src/pages/Home/HomeAttentionRows.tsx`, and
  `src/features/operations/api.ts`.
- Each of four Home-spec suites uses `home-coverage-fixture.js`, archives previous
  browser navigations and records an individual coverage snapshot per test.
- `--list` independently counts Playwright-discovered cases. Missing
  suite/test, absent mapped original TSX source, missing source maps,
  partial browser snapshots or archiving error make the CI job fail closed.
- `check-home-frontend-coverage.cjs` enforces 100% *per file*
  in statements, lines, branches, and functions, reports all uncovered branches
  and locations, and provides a source-remapped JSON/Markdown artifact.
- The existing Inventory-specific 95% exception for PR #768 does not apply to
  PR #780, Home, or any other files.

## Outstanding explicit holds

1. Full current-HEAD browser CI and Home source report must show GREEN.
2. Every uncovered Home/operations-api path found by the source report must gain
   meaningful assertions at the lowest applicable test level; never delete
   instrumentation, skip a branch, fabricate coverage, or lower the threshold.
3. The reused history/audit panel's independent control inventory and its
   underlying tests must be cross-checked before declaring a product-wide
   interaction total.
4. Final branch vs current main and post-merge exact-main verification are
   reserved for Part 8. No real telephone, messaging, payment, Railway or
   production database side effects are permitted in these tests.
