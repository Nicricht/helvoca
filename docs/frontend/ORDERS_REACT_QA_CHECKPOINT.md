# Orders React QA checkpoint

This checkpoint belongs to PR #710 on `feat/react-orders-operations-migration`.

## Proven RED

At RED head `892b1e90620aad20029e174ed71ae66843151983`, the eight permanent Orders browser contracts failed because `/app/orders` did not exist. Existing tests remained green.

## GREEN implementation before final exact-head run

The branch now contains:

- Orders API client with no browser-supplied `businessId`
- permission-aware Orders workspace state
- React Orders page with search, filters and sorting
- embedded Customers and Conversations perspectives
- detail drawer with products, authoritative totals, fulfillment and source
- optional history and conversation/call context
- role-aware state transitions
- duplicate-submit protection and authoritative refetch
- bounded loading, empty, partial and error states
- responsive mobile order list and full-screen detail
- dark-first Helvoca styling and reduced-motion support
- direct `/app/orders` static SPA output and Spring forwarding

## Intermediate CI evidence

Run #3245 (`6671d1d93c7cff104b898473b1b3abbfabc31f54`) contains the complete Orders feature code before direct-route wiring:

- Fast Gate: success
- Golden Journey: success
- backend tests: success
- differential Java coverage: success
- React `tsc --noEmit && vite build`: success
- existing browser suite: 207 passed
- Orders contracts: 8 failed only because `GET /app/orders` returned 404 at that pre-wiring commit

The commits after that run add the direct React route, static build copy, Spring forwarding, permission-gated optional delivery reads, simplified navigation and migration documentation.

The next PR run must certify the exact current head before this PR may advance beyond Draft.
