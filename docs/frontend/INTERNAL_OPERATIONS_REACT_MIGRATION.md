# Internal Operations React migration

Branch: `feat/react-internal-operations-migration`

Base main: `4a4602c85725411c71092fa2307f40d77b3f5e85`

FRAME CHANGE: NO

## Audit decision

`operations.html` must **not** be redirected to `/app/orders` and must not be deleted as a duplicate.

The current legacy surface is an internal pilot-certification and launch-control tool. React Orders is the customer-facing commercial order workspace. The two surfaces share the word “operations” but serve different product and safety responsibilities.

The safe target is a specialized, non-navigation React route:

`/app/internal/operations`

The legacy `/operations.html` path should become compatibility-only and redirect to that internal route after parity is proven.

## Parity map

| Legacy capability | React equivalent today | Missing behavior | API | E2E / contract | Risk | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| Internal role guard for BUSINESS_ADMIN / OPERATOR | AuthBoundary only proves token presence | Internal page role gate | `GET /api/v1/auth/me` | `e2e/operations-internal.spec.js` | HIGH | Preserve on specialized internal React route |
| Pilot readiness score and component blockers | None in React Orders; React Home explicitly rejects this dependency | Full readiness card | `GET /api/v1/operations/pilot-readiness` | `e2e/operations-internal.spec.js` | HIGH | Migrate |
| Pilot lifecycle configuration | None in React Orders | Responsible person, contact, measurable goal, planned end | `GET/PUT /api/v1/operations/pilot-control` | controller contract + operations E2E | HIGH | Migrate |
| Start / pause / resume / complete pilot | None in React Orders | Lifecycle actions and concurrency lock | `POST /api/v1/operations/pilot-control/{start,pause,resume,complete}` | controller/service tests + operations E2E | HIGH | Migrate with exact backend semantics unchanged |
| GO / NO-GO launch cage | None in React Orders | Decision, traffic mode, blockers, checks, snapshot | `GET /api/v1/operations/pilot-preflight` | `e2e/pilot-preflight.spec.js`, release-readiness gate | HIGH | Migrate |
| Pilot metrics today / 7 days | React Orders has order KPIs only; React Home explicitly rejects pilot metrics | Calls, WhatsApp, bookings, orders, payments, revenue, transfers and failure rates | `GET /api/v1/operations/pilot-metrics` | `e2e/operations-internal.spec.js`, service tests | MEDIUM | Migrate |
| Customer order management | Full React Orders implementation | None for this capability | commercial order APIs | `e2e/react-orders.spec.js` | MEDIUM/HIGH | Already owned by `/app/orders`; do not duplicate |
| Customers / conversations as order context | Embedded in React Orders | None | order/context APIs | `e2e/react-orders.spec.js`, cutover contracts | MEDIUM | Already migrated; do not add here |
| Legacy script shell | No equivalent needed | Compatibility routing only | none | frontend/asset contracts | LOW | Retire after React parity |
| Legacy standalone pilot JS assets | No React equivalent yet | React components/API client | operations pilot APIs | current operations/preflight E2E | HIGH | Retire only after React tests replace direct script dependency |

## Dependency audit

### Browser scripts owned by the legacy internal surface

- `operations.js`
- `pilot-readiness.js`
- `pilot-control.js`
- `pilot-preflight.js`
- `pilot-metrics.js`

The four pilot scripts are loaded by `operations.html`. `pilot-preflight.js` is also loaded directly by the Playwright fixture in `e2e/pilot-preflight.spec.js`, so that contract must be migrated before the asset can be removed.

### Backend APIs

The legacy surface uses:

- `GET /api/v1/auth/me`
- `GET /api/v1/operations/pilot-readiness`
- `GET /api/v1/operations/pilot-metrics`
- `GET /api/v1/operations/pilot-preflight`
- `GET /api/v1/operations/pilot-control`
- `PUT /api/v1/operations/pilot-control`
- `POST /api/v1/operations/pilot-control/start`
- `POST /api/v1/operations/pilot-control/pause`
- `POST /api/v1/operations/pilot-control/resume`
- `POST /api/v1/operations/pilot-control/complete`

The page does not own order-management APIs. Those belong to React Orders.

### E2E / CI dependencies

- `e2e/operations-internal.spec.js` certifies the internal surface and its unauthenticated redirect.
- `e2e/pilot-preflight.spec.js` directly loads the legacy preflight script.
- `scripts/ci/commercial-release-readiness-first-business.sh` explicitly runs both contracts.
- `e2e/frontend-foundation.spec.js` includes `/operations.html` in legacy surface coverage.
- `e2e/frontend-finish-integration.spec.js` ensures normal customer navigation does not link to `/operations.html`.
- `ConsoleScriptAssetsContractTest` parses legacy operation assets.
- `SecurityConfig.PUBLIC_CONSOLE_ASSETS` currently exposes the legacy HTML and JS assets.

### Stale or misleading links

`business-activation-guide.js` and `BusinessActivationGuideService` point pilot setup to `/#pilotControlCard`. React Home deliberately does not load pilot readiness or pilot metrics, so this link should move to the specialized internal React route as part of the cutover.

## Safety constraints

This is HIGH risk because pilot lifecycle state participates in the external-effect guard. The migration must not weaken:

- tenant isolation;
- simulator/test external-side-effect safety;
- final-state truth;
- verification freshness;
- the controlled-pilot global safety switch;
- backend role enforcement.

The browser must never enable the global external-effects switch. A UI `GO` result is readiness evidence only and must never be represented as provider activation or a completed external effect.

## Acceptance criteria

1. `/app/internal/operations` renders only for BUSINESS_ADMIN / OPERATOR and remains absent from normal customer navigation.
2. All four internal capability groups have parity: readiness, lifecycle control, launch cage, metrics.
3. Start/pause/resume/complete remain backend-authoritative and cannot double-submit while a mutation is in flight.
4. Loading, partial/error and terminal states are explicit.
5. Reduced motion and responsive containment are preserved.
6. `/operations.html` becomes compatibility-only after parity and redirects to `/app/internal/operations`.
7. Legacy operations/pilot JS assets are removed only after no runtime or certification test still depends on them.
8. Commercial release-readiness CI continues to certify the replacement tests.
9. No real call, WhatsApp message, booking, order, payment or provider activation is triggered by migration tests.

## RED-first plan

The first RED contract will require the specialized React route and compatibility redirect while preserving the internal-only capability contract. It should fail on the current base because the React route does not exist and `operations.html` still serves the legacy application.
